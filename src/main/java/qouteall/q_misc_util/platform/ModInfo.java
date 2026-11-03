package qouteall.q_misc_util.platform;

import java.util.Optional;
import org.jetbrains.annotations.Nullable;

public record ModInfo(String id, String name, String version, @Nullable int[] semanticVersionComponents, Optional<String> iconPath, boolean isNested) {
}
