package com.avanza.astrix.intellij;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.codeInsight.daemon.MergeableLineMarkerInfo;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.concurrency.JobLauncher;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.avanza.astrix.intellij.AstrixContextUtility.findBeanUsages;
import static com.avanza.astrix.intellij.AstrixContextUtility.isBeanDeclaration;
import static com.intellij.openapi.util.NotNullLazyValue.lazy;
import static java.util.stream.Collectors.toList;

public class AstrixBeanDeclarationLineMarker extends LineMarkerProviderDescriptor {
    private final Icon icon = Icons.Gutter.asterisk;
    private final Option beanOption = new Option("astrix.bean", "Astrix bean", icon);

    @Nullable
    @Override
    public final LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        return null;
    }

    @Override
    public void collectSlowLineMarkers(@NotNull List<? extends PsiElement> elements, @NotNull Collection<? super LineMarkerInfo<?>> result) {
        ApplicationManager.getApplication().assertReadAccessAllowed();

        if (beanOption.isEnabled()) {
            final Object lock = new Object();
            ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
            JobLauncher.getInstance().invokeConcurrentlyUnderProgress(elements, indicator, element -> {
                createLineMarkerInfo(element).ifPresent(info -> {
                    synchronized (lock) {
                        result.add(info);
                    }
                });
                return true;
            });
        }
    }

    private Optional<MergeableLineMarkerInfo<?>> createLineMarkerInfo(@NotNull PsiElement element) {
        PsiElement parent;
        if (element instanceof PsiIdentifier && (parent = element.getParent()) instanceof PsiMethod) {
            PsiMethod method = (PsiMethod) parent;

            if (isBeanDeclaration(method)) {
                return Optional.of(NavigationGutterIconBuilder.create(icon)
                                                              .setEmptyPopupText("No astrix bean usages found.")
                                                              .setTargets(lazy(() -> findBeanUsages(method).stream()
                                                                                                           .flatMap(query -> query.findAll().stream())
                                                                                                           .collect(toList())))
                                                              .createLineMarkerInfo(element));
            }
        }
        return Optional.empty();
    }

    @Nullable
    @Override
    public String getName() {
        return "Astrix bean declaration";
    }

    @Override
    public Option @NotNull [] getOptions() {
        return new Option[]{beanOption};
    }

}
