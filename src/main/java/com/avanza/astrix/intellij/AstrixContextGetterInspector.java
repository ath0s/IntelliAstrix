package com.avanza.astrix.intellij;

import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class AstrixContextGetterInspector extends BaseJavaBatchLocalInspectionTool {

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new AstrixContextGetterVisitor(holder);
    }


    private class AstrixContextGetterVisitor extends JavaElementVisitor {
        private final ProblemsHolder problemsHolder;

        public AstrixContextGetterVisitor(ProblemsHolder holder) {
            problemsHolder = holder;
        }

        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);

            AstrixContextUtility utility = new AstrixContextUtility();

            PsiMethod method = expression.resolveMethod();
            if (utility.isAstrixBeanRetriever(method) && !utility.findBeanDeclaration(expression.getArgumentList()).isPresent()) {
                problemsHolder.registerProblem(expression.getArgumentList().getExpressions()[0], "No astrix bean declaration found.",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            }
        }
    }
}
