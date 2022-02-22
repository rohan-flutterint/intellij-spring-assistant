package in.oneton.idea.spring.assistant.plugin.misc;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import javax.swing.*;

import static com.intellij.openapi.util.IconLoader.getIcon;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Icons {

    public static final Icon SpringBoot = getIcon("/spring-boot.svg", Icons.class);
    public static final Icon SpringInitializr = getIcon("/spring-initializr.svg", Icons.class);
    public static final Icon SpringInitializrMini = getIcon("/spring-initializr.png", Icons.class);

}
