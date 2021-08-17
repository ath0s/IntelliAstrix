package com.avanza.astrix.intellij

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.concurrency.JobLauncher
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiMethod

class AstrixBeanDeclarationUnusedInspectionSuppressor : InspectionSuppressor {

    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        val method = (element as? PsiIdentifier)?.parent as? PsiMethod ?: return false
        if (AstrixContextUtility.isBeanDeclaration(method)) {
            return !JobLauncher.getInstance().invokeConcurrentlyUnderProgress(AstrixContextUtility.findBeanUsages(method), ProgressIndicatorProvider.getGlobalProgressIndicator()) {
                it.findFirst() != null
            }
        }
        return false
    }

    override fun getSuppressActions(element: PsiElement?, toolId: String) =
        emptyArray<SuppressQuickFix>()
}