package com.avanza.astrix.intellij

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.openapi.util.NotNullLazyValue.atomicLazy
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope

class AstrixContextGetterInspector : AbstractBaseJavaLocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        AstrixContextGetterVisitor(holder)

    private inner class AstrixContextGetterVisitor(
        private val problemsHolder: ProblemsHolder
    ) : JavaElementVisitor() {

        private val candidates: NotNullLazyValue<Collection<PsiMethod>> = atomicLazy {
            val file = problemsHolder.file
            val globalSearchScope = getSearchScope(file)
            if (globalSearchScope == null) {
                emptyList()
            } else {
                AstrixContextUtility.getBeanDeclarationCandidates(globalSearchScope, file.project)
            }
        }

        override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
            super.visitMethodCallExpression(expression)
            val method = expression.resolveMethod()
            if (AstrixContextUtility.isAstrixBeanRetriever(method) && !hasBeanDeclaration(expression.argumentList)) {
                problemsHolder.registerProblem(expression.argumentList, "No astrix bean declaration found.", GENERIC_ERROR_OR_WARNING)
            }
        }

        private fun hasBeanDeclaration(psiExpressionList: PsiExpressionList): Boolean {
            val isBeanDeclaration = AstrixContextUtility.isBeanDeclaration(psiExpressionList)::test
            return candidates.value.any ( isBeanDeclaration )
        }

    }

    private fun getSearchScope(file: PsiFile): GlobalSearchScope? {
        val virtualFile = file.virtualFile ?: return null
        val module = ModuleUtil.findModuleForFile(virtualFile, file.project) ?: return null
        val fileIndex = ModuleRootManager.getInstance(module).fileIndex
        val includeTests = fileIndex.isInTestSourceContent(virtualFile)
        return module.getModuleRuntimeScope(includeTests)
    }
}