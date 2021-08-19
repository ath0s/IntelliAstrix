package com.avanza.astrix.intellij

import com.avanza.astrix.intellij.AstrixContextUtility.isAstrixBeanRetriever
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.openapi.util.NotNullLazyValue.atomicLazy
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiExpressionList
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.search.GlobalSearchScope

class AstrixContextGetterInspector : AbstractBaseJavaLocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        AstrixContextGetterVisitor(holder)

    private inner class AstrixContextGetterVisitor(
        private val problemsHolder: ProblemsHolder
    ) : JavaElementVisitor() {

        private val candidates: NotNullLazyValue<Collection<PsiMethod>> = atomicLazy {
            val file = problemsHolder.file
            val globalSearchScope = file.getSearchScope()
            if (globalSearchScope == null) {
                emptyList()
            } else {
                AstrixContextUtility.getBeanDeclarationCandidates(globalSearchScope, file.project)
            }
        }

        override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
            super.visitMethodCallExpression(expression)
            val method = expression.resolveMethod()
            if (method?.isAstrixBeanRetriever == true && !expression.argumentList.hasBeanDeclaration) {
                problemsHolder.registerProblem(
                    expression.argumentList,
                    "No astrix bean declaration found.",
                    GENERIC_ERROR_OR_WARNING
                )
            }
        }

        private val PsiExpressionList.hasBeanDeclaration: Boolean
            get() {
                val isBeanDeclaration = AstrixContextUtility.isBeanDeclaration(this)
                return candidates.value.any(isBeanDeclaration)
            }

    }

    private fun PsiFile.getSearchScope(): GlobalSearchScope? {
        val virtualFile = virtualFile ?: return null
        val module = ModuleUtil.findModuleForFile(virtualFile, project) ?: return null
        val fileIndex = ModuleRootManager.getInstance(module).fileIndex
        val includeTests = fileIndex.isInTestSourceContent(virtualFile)
        return module.getModuleRuntimeScope(includeTests)
    }
}