package com.avanza.astrix.intellij;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static com.avanza.astrix.intellij.AstrixContextUtility.isAstrixBeanRetriever;
import static com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
import static com.intellij.openapi.util.NotNullLazyValue.atomicLazy;
import static java.util.Collections.emptyList;

public class AstrixContextGetterInspector extends AbstractBaseJavaLocalInspectionTool {

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new AstrixContextGetterVisitor(holder);
    }

    private class AstrixContextGetterVisitor extends JavaElementVisitor {
        private final ProblemsHolder problemsHolder;
        private final NotNullLazyValue<Collection<PsiMethod>> candidates;

        AstrixContextGetterVisitor(ProblemsHolder problemsHolder) {
            this.problemsHolder = problemsHolder;
            this.candidates = atomicLazy(() -> {
                PsiFile file = problemsHolder.getFile();
                GlobalSearchScope globalSearchScope = getSearchScope(file);
                if (globalSearchScope == null) {
                    return emptyList();
                } else {
                    return AstrixContextUtility.getBeanDeclarationCandidates(globalSearchScope, file.getProject());
                }
            });
        }

        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);


            PsiMethod method = expression.resolveMethod();
            if (isAstrixBeanRetriever(method) && !hasBeanDeclaration(expression.getArgumentList())) {
                problemsHolder.registerProblem(expression.getArgumentList(), "No astrix bean declaration found.", GENERIC_ERROR_OR_WARNING);
            }
        }

        private boolean hasBeanDeclaration(PsiExpressionList psiExpressionList) {
            return candidates.getValue().stream().anyMatch(AstrixContextUtility.isBeanDeclaration(psiExpressionList));
        }
    }

    @Nullable
    private GlobalSearchScope getSearchScope(PsiFile file) {
        VirtualFile virtualFile;
        Module module;
        if ((virtualFile = file.getVirtualFile()) == null || (module = ModuleUtil.findModuleForFile(virtualFile, file.getProject())) == null) {
            return null;
        }

        ModuleFileIndex fileIndex = ModuleRootManager.getInstance(module).getFileIndex();
        boolean includeTests = fileIndex.isInTestSourceContent(virtualFile);
        return module.getModuleRuntimeScope(includeTests);
    }
}
