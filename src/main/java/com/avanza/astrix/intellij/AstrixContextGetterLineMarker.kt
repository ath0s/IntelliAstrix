package com.avanza.astrix.intellij

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.concurrency.JobLauncher
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.NotNullLazyValue.lazy
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.search.GlobalSearchScope
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class AstrixContextGetterLineMarker : LineMarkerProviderDescriptor() {
    private val astrixIcon = Icons.Gutter.asterisk
    private val getterOption = Option("astrix.getter", "Astrix getter", astrixIcon)

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>?>) {
        ApplicationManager.getApplication().assertReadAccessAllowed()
        if (getterOption.isEnabled) {
            val candidatesByModule = ConcurrentHashMap<GlobalSearchScope, Collection<PsiMethod>>()
            val lock = ReentrantLock()
            val indicator = ProgressIndicatorProvider.getGlobalProgressIndicator()
            JobLauncher.getInstance().invokeConcurrentlyUnderProgress(elements, indicator) { it ->
                createLineMarkerInfo(it, candidatesByModule)?.apply { lock.withLock{ result.add(this) } }
                true
            }
        }
    }

    private fun createLineMarkerInfo(element: PsiElement, candidatesByModule: ConcurrentMap<GlobalSearchScope, Collection<PsiMethod>>): LineMarkerInfo<*>? {
        val psiMethodCallExpression = (element as? PsiReferenceExpression)?.parent as? PsiMethodCallExpression ?: return null
        if (AstrixContextUtility.isAstrixBeanRetriever(psiMethodCallExpression.resolveMethod())) {
            val globalSearchScope = getSearchScope(element) ?: return null
            val candidates = candidatesByModule.computeIfAbsent(globalSearchScope) { AstrixContextUtility.getBeanDeclarationCandidates(globalSearchScope, element.getProject()) }
            val isBeanDeclaration = AstrixContextUtility.isBeanDeclaration(psiMethodCallExpression.argumentList)::test
            return candidates.firstOrNull(isBeanDeclaration)?.let {
                NavigationGutterIconBuilder.create(astrixIcon)
                    .setTargets(lazy { candidates.filter(isBeanDeclaration) })
                    .setTooltipText(getTooltipText(it))
                    .createLineMarkerInfo(element)
            }
        }
        return null
    }

    private fun getSearchScope(element: PsiElement): GlobalSearchScope? {
        val module = ModuleUtil.findModuleForPsiElement(element) ?: return null
        val virtualFile = element.containingFile.virtualFile ?: return null
        val fileIndex = ModuleRootManager.getInstance(module).fileIndex
        val includeTests = fileIndex.isInTestSourceContent(virtualFile)
        return module.getModuleRuntimeScope(includeTests)
    }

    private fun getTooltipText(method: PsiMethod): String {
        return buildString {
            append("<html><body>")
            if (AstrixContextUtility.isService(method)) {
                append("<b>", "Service", "</b><br/>")
            } else if (AstrixContextUtility.isLibrary(method)) {
                append("<b>","Library","</b><br/>")
            }
            append("Navigate to bean declaration")
            val returnType = method.returnType
            if (returnType != null) {
                append(" of ", returnType.presentableText)
            }
            append("</body></html>")
        }
    }

    override fun getName() =
        "Astrix bean retrieval"

    override fun getOptions() =
        arrayOf(getterOption)
}