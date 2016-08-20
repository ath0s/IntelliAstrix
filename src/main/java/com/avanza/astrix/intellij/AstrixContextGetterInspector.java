package com.avanza.astrix.intellij;

import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import org.jetbrains.annotations.NotNull;

import static com.avanza.astrix.intellij.AstrixContextUtility.findBeanDeclaration;
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

        public AstrixContextGetterVisitor(ProblemsHolder holder) {
            problemsHolder = holder;
        }

        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);


            PsiMethod method = expression.resolveMethod();
            if (isAstrixBeanRetriever(method) && !findBeanDeclaration(expression.getArgumentList()).isPresent()) {
                problemsHolder.registerProblem(expression.getArgumentList().getExpressions()[0], "No astrix bean declaration found.", GENERIC_ERROR_OR_WARNING);
            }
        }
    }
}
