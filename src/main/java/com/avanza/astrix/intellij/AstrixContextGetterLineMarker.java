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
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import static com.avanza.astrix.intellij.AstrixContextUtility.isAstrixBeanRetriever;
import static com.avanza.astrix.intellij.AstrixContextUtility.isBeanDeclaration;
import static java.util.stream.Collectors.toList;

public class AstrixContextGetterLineMarker extends LineMarkerProviderDescriptor {
    private final Icon icon = Icons.Gutter.asterisk;
    private final Option getterOption = new Option("astrix.getter", "Astrix getter", icon);

    @Nullable
    @Override
    public LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement element) {
        return null;
    }

    @Override
    public void collectSlowLineMarkers(@NotNull List<PsiElement> elements, @NotNull Collection<LineMarkerInfo> result) {
        ApplicationManager.getApplication().assertReadAccessAllowed();

        if (getterOption.isEnabled()) {
            ConcurrentMap<Module, Collection<PsiMethod>> candidatesByModule = new ConcurrentHashMap<>();

            List<Computable<Optional<LineMarkerInfo>>> tasks = elements.stream()
                                                                       .map(createLineMarkerInfoTask(candidatesByModule))
                                                                       .collect(toList());
            final Object lock = new Object();
            ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
            JobLauncher.getInstance().invokeConcurrentlyUnderProgress(tasks, indicator, true, computable -> {
                computable.compute()
                          .ifPresent(info -> {
                              synchronized (lock) {
                                  result.add(info);
                              }
                          });
                return true;
            });

        }
    }

    private Function<PsiElement, Computable<Optional<LineMarkerInfo>>> createLineMarkerInfoTask(ConcurrentMap<Module, Collection<PsiMethod>> candidatesByModule) {
        return element -> () -> createLineMarkerInfo(element, candidatesByModule);
    }

    private Optional<LineMarkerInfo> createLineMarkerInfo(PsiElement element, ConcurrentMap<Module, Collection<PsiMethod>> candidatesByModule) {
        PsiElement parent;
        if (element instanceof PsiReferenceExpression && (parent = element.getParent()) instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression psiMethodCallExpression = (PsiMethodCallExpression) parent;

            if (isAstrixBeanRetriever(psiMethodCallExpression.resolveMethod())) {
                Module module = ModuleUtil.findModuleForPsiElement(element);
                Collection<PsiMethod> candidates = candidatesByModule.computeIfAbsent(module, AstrixContextUtility::getBeanDeclarationCandidates);

                return candidates.stream()
                                 .filter(isBeanDeclaration(psiMethodCallExpression.getArgumentList()))
                                 .findFirst()
                                 .map(method -> NavigationGutterIconBuilder.create(icon)
                                                                           .setTarget(method.getNameIdentifier())
                                                                           // TODO: Indicate whether bean is a Service or a Library
                                                                           .setTooltipText("Navigate to declaration of " + Optional.ofNullable(method.getReturnType())
                                                                                                                                   .map(PsiType::getPresentableText)
                                                                                                                                   .orElse(""))
                                                                           .createLineMarkerInfo(element));
            }
        }
        return Optional.empty();
    }

    @Nullable
    @Override
    public String getName() {
        return "Astrix line markers";
    }

    @Override
    public Option[] getOptions() {
        return new Option[]{getterOption};
    }
}
