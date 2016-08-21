package com.avanza.astrix.intellij;

import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.avanza.astrix.intellij.AstrixContextUtility.isAstrixBeanRetriever;
import static com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING;

public class AstrixContextGetterInspector extends BaseJavaBatchLocalInspectionTool {

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new AstrixContextGetterVisitor(holder);
    }

    private class AstrixContextGetterVisitor extends JavaElementVisitor {
        private final ProblemsHolder problemsHolder;
        private final AtomicNotNullLazyValue<Collection<PsiMethod>> candidates;

        public AstrixContextGetterVisitor(ProblemsHolder problemsHolder) {
            this.problemsHolder = problemsHolder;
            this.candidates = new AtomicNotNullLazyValue<Collection<PsiMethod>>() {
                @NotNull
                @Override
                protected Collection<PsiMethod> compute() {
                    Module module = ModuleUtil.findModuleForPsiElement(problemsHolder.getFile());
                    return AstrixContextUtility.getBeanDeclarationCandidates(module);
                }
            };
        }

        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);


            PsiMethod method = expression.resolveMethod();
            if (isAstrixBeanRetriever(method) && !hasBeanDeclaration(expression.getArgumentList())) {
                problemsHolder.registerProblem(expression.getArgumentList().getExpressions()[0], "No astrix bean declaration found.", GENERIC_ERROR_OR_WARNING);
            }
        }

        private boolean hasBeanDeclaration(PsiExpressionList psiExpressionList) {
            return candidates.getValue().stream().anyMatch(AstrixContextUtility.isBeanDeclaration(psiExpressionList));
        }
    }
}
