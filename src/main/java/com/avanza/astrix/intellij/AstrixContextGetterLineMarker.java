package com.avanza.astrix.intellij;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.concurrency.JobLauncher;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

import static com.avanza.astrix.intellij.AstrixContextUtility.*;
import static java.util.stream.Collectors.toList;

public class AstrixContextGetterLineMarker extends LineMarkerProviderDescriptor {
    private final Icon icon = Icons.Gutter.asterisk;
    private final Option getterOption = new Option("astrix.getter", "Astrix getter", icon);

    @Nullable
    @Override
    public final LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement element) {
        return null;
    }

    @Override
    public void collectSlowLineMarkers(@NotNull List<PsiElement> elements, @NotNull Collection<LineMarkerInfo> result) {
        ApplicationManager.getApplication().assertReadAccessAllowed();

        if (getterOption.isEnabled()) {
            ConcurrentMap<GlobalSearchScope, Collection<PsiMethod>> candidatesByModule = new ConcurrentHashMap<>();

            final Object lock = new Object();
            ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
            JobLauncher.getInstance().invokeConcurrentlyUnderProgress(elements, indicator, true, element -> {
                createLineMarkerInfo(element, candidatesByModule).ifPresent(info -> {
                    synchronized (lock) {
                        result.add(info);
                    }
                });
                return true;
            });

        }
    }

    private Optional<LineMarkerInfo> createLineMarkerInfo(PsiElement element, ConcurrentMap<GlobalSearchScope, Collection<PsiMethod>> candidatesByModule) {
        PsiElement parent;
        if (element instanceof PsiReferenceExpression && (parent = element.getParent()) instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression psiMethodCallExpression = (PsiMethodCallExpression) parent;

            GlobalSearchScope globalSearchScope;
            if (isAstrixBeanRetriever(psiMethodCallExpression.resolveMethod()) && (globalSearchScope = getSearchScope(element)) != null) {
                Collection<PsiMethod> candidates = candidatesByModule.computeIfAbsent(globalSearchScope,
                                                                                      searchScope -> getBeanDeclarationCandidates(searchScope, element.getProject()));
                Predicate<PsiMethod> isBeanDeclaration = isBeanDeclaration(psiMethodCallExpression.getArgumentList());
                return candidates.stream()
                                 .filter(isBeanDeclaration)
                                 .findFirst()
                                 .map(beanDeclarationMethod -> NavigationGutterIconBuilder.create(icon)
                                                                           .setTargets(new NotNullLazyValue<Collection<? extends PsiElement>>() {
                                                                               @NotNull
                                                                               @Override
                                                                               protected Collection<? extends PsiElement> compute() {
                                                                                   return candidates.stream().filter(isBeanDeclaration).collect(toList());
                                                                               }
                                                                           })
                                                                           .setTooltipText(getTooltipText(beanDeclarationMethod))
                                                                           .createLineMarkerInfo(element));
            }
        }
        return Optional.empty();
    }

    @Nullable
    private GlobalSearchScope getSearchScope(PsiElement element) {
        Module module;
        VirtualFile virtualFile;
        if ((module = ModuleUtil.findModuleForPsiElement(element)) == null || (virtualFile = element.getContainingFile().getVirtualFile()) == null) {
            return null;
        }

        ModuleFileIndex fileIndex = ModuleRootManager.getInstance(module).getFileIndex();
        boolean includeTests = fileIndex.isInTestSourceContent(virtualFile);
        return module.getModuleRuntimeScope(includeTests);
    }

    private String getTooltipText(PsiMethod method) {
        StringBuilder sb = new StringBuilder("<html><body>");
        if(isService(method)) {
            sb.append("<b>").append("Service").append("</b><br/>");
        } else if(isLibrary(method)) {
            sb.append("<b>").append("Library").append("</b><br/>");
        }
        sb.append("Navigate to bean declaration");
        PsiType returnType = method.getReturnType();
        if(returnType != null) {
            sb.append(" of ").append(returnType.getPresentableText());
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    @Nullable
    @Override
    public String getName() {
        return "Astrix bean retrieval";
    }

    @Override
    public Option[] getOptions() {
        return new Option[]{getterOption};
    }
}
