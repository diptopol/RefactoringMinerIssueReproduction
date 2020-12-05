import gr.uom.java.xmi.LocationInfo;
import gr.uom.java.xmi.diff.ExtractOperationRefactoring;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.refactoringminer.api.*;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;
import org.refactoringminer.util.GitServiceImpl;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Diptopol
 * @since 12/5/2020 11:33 AM
 */
public class App {

    private static GitService gitService;
    private static Repository repository;

    public static void main(String[] args) {
        gitService = new GitServiceImpl();

        String projectFullName = "Guardsquare/proguard";
        String projectUrl = "https://github.com/Guardsquare/proguard";
        String repositoryLocalDirectory = getRepositoryLocalDirectory(projectFullName);

        try {
            repository = gitService.cloneIfNotExists(
                    repositoryLocalDirectory,
                    projectUrl);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        List<String> commitIdIdList = new ArrayList<>();
        commitIdIdList.add("824e6abb1f524be726b1a207b7a2231aa9a4f95c");
        commitIdIdList.add("3fd802b3604e22f3d600cafab52e4491561583c8");
        commitIdIdList.add("ec3d479d473c9d09d4278b1aa87bc943d40d4d10");
        commitIdIdList.add("f63565a45b739f00a8518b234993337d22f54e27");


        for (String commidId : commitIdIdList) {
            List<Pair<String, Refactoring>> refactoringPairList = detectExtractMethodRefactoringData(commidId);
            performCodeExtraction(refactoringPairList);
        }
    }

    public static List<Pair<String, Refactoring>> detectExtractMethodRefactoringData(String commitId) {
        GitHistoryRefactoringMiner miner = new GitHistoryRefactoringMinerImpl();

        List<Pair<String, Refactoring>> refactoringPairList = new ArrayList<>();

        try {
            RefactoringHandler refactoringHandler = new RefactoringHandler() {

                public void handle(String commitId, List<Refactoring> refs) {
                    if (refs == null)
                        return;

                    for (Refactoring ref : refs) {
                        if (ref.getRefactoringType() == RefactoringType.EXTRACT_OPERATION) {
                            refactoringPairList.add(new MutablePair<>(commitId, ref));
                        }
                    }
                }
            };

            miner.detectAtCommit(repository, commitId, refactoringHandler, 30);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return refactoringPairList;
    }


    public static String getRepositoryLocalDirectory(String projectFullName) {
        return "projectDirectory/" + projectFullName.replaceAll("/", "-");
    }

    private static void performCodeExtraction(List<Pair<String, Refactoring>> refactoringPairList) {
        for (Pair<String, Refactoring> pair : refactoringPairList) {

            String commitId = pair.getLeft();
            ExtractOperationRefactoring refactoring = (ExtractOperationRefactoring) pair.getRight();

            LocationInfo locationInfo = refactoring.getSourceOperationAfterExtraction().getLocationInfo();
            String filePath = locationInfo.getFilePath();

            try {
                String wholeText = getWholeTextFromFile(locationInfo, repository, commitId);
                getMethodDeclaration(filePath, wholeText, locationInfo.getStartOffset(), locationInfo.getLength());

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public static MethodDeclaration getMethodDeclaration(String file, String wholeText, int startOffSet, int length)
            throws IOException, Exception {
        MethodDeclaration methodDeclaration;
        try {
            ASTParser parser = ASTParser.newParser(AST.JLS8);
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            Map options = JavaCore.getOptions();
            JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
            parser.setCompilerOptions(options);
            parser.setResolveBindings(false);
            parser.setEnvironment(new String[0], new String[]{file}, null, false);
            parser.setSource(wholeText.toCharArray());
            parser.setResolveBindings(true);
            CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
            ASTNode block = NodeFinder.perform(compilationUnit, startOffSet,
                    length);

            methodDeclaration = (MethodDeclaration) block;
        } catch (Exception e) {
            throw e;
        }
        return methodDeclaration;
    }

    public static String getWholeTextFromFile(LocationInfo locationInfo, Repository repository, String commitId)
            throws IOException, Exception {
        try {
            if (!repository.getFullBranch().equals(commitId))
                new GitServiceImpl().checkout(repository, commitId);

            String wholeText = readFile(new String(repository.getDirectory().getAbsolutePath().replaceAll("\\.git", "")
                    + "/" + locationInfo.getFilePath()), StandardCharsets.UTF_8);
            return wholeText;
        } catch (Exception e) {
            revert(repository);

            throw e;
        }
    }

    private static String readFile(String path, Charset encoding) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded);
    }

    private static boolean revert(Repository repository) {
        try (Git git = new Git(repository)) {
            git.revert().setStrategy(MergeStrategy.THEIRS).call();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

}
