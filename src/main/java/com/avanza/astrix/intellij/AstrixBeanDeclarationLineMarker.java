package com.avanza.astrix.intellij;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class AstrixBeanDeclarationLineMarker extends RelatedItemLineMarkerProvider {
    private final Option beanOption = new Option("astrix.bean", "Astrix bean", Icons.Gutter.asterisk);

    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element, Collection<? super RelatedItemLineMarkerInfo> result) {
        PsiElement parent;
        if (element instanceof PsiIdentifier && beanOption.isEnabled() && (parent = element.getParent()) instanceof PsiMethod) {
            PsiMethod method = (PsiMethod) parent;
            AstrixContextUtility utility = new AstrixContextUtility();

            if (utility.isBeanDeclaration(method)) {
                RelatedItemLineMarkerInfo<PsiElement> lineMarkerInfo = NavigationGutterIconBuilder.create(beanOption.getIcon())
                        .setTargets(new NotNullLazyValue<Collection<? extends PsiElement>>() {
                            @NotNull
                            @Override
                            protected Collection<? extends PsiElement> compute() {
                                return utility.findBeanUsages(method);
                            }
                        })
                        .createLineMarkerInfo(element);
                result.add(lineMarkerInfo);
            }
        }
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
