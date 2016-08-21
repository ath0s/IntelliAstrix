package com.avanza.astrix.intellij;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.codeInsight.daemon.MergeableLineMarkerInfo;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static com.avanza.astrix.intellij.AstrixContextUtility.findBeanUsages;
import static com.avanza.astrix.intellij.AstrixContextUtility.isBeanDeclaration;

public class AstrixBeanDeclarationLineMarker extends LineMarkerProviderDescriptor {
    private final Option beanOption = new Option("astrix.bean", "Astrix bean", Icons.Gutter.asterisk);

    @Nullable
    @Override
    public LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement element) {
        return null;
    }

    @Override
    public void collectSlowLineMarkers(@NotNull List<PsiElement> elements, @NotNull Collection<LineMarkerInfo> result) {
        ApplicationManager.getApplication().assertReadAccessAllowed();
        elements.stream().map(this::collectNavigationMarkers).filter(Objects::nonNull).forEach(result::add);
    }

    @Nullable
    private MergeableLineMarkerInfo<PsiElement> collectNavigationMarkers(@NotNull PsiElement element) {
        PsiElement parent;
        if (element instanceof PsiIdentifier && beanOption.isEnabled() && (parent = element.getParent()) instanceof PsiMethod) {
            PsiMethod method = (PsiMethod) parent;

            if (isBeanDeclaration(method)) {
                return NavigationGutterIconBuilder.create(Icons.Gutter.asterisk)
                        .setTargets(new NotNullLazyValue<Collection<? extends PsiElement>>() {
                            @NotNull
                            @Override
                            protected Collection<? extends PsiElement> compute() {
                                return findBeanUsages(method);
                            }
                        })
                        .createLineMarkerInfo(element);
            }
        }
        return null;
    }

    @Nullable
    @Override
    public String getName() {
        return "Astrix bean declaration";
    }

    @Override
    public Option[] getOptions() {
        return new Option[]{beanOption};
    }

}
