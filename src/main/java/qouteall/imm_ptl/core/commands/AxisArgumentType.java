package qouteall.imm_ptl.core.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.serialize.ConstantArgumentSerializer;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction.class_2351;
import qouteall.imm_ptl.core.McHelper;
import qouteall.q_misc_util.platform.Platform;

public class AxisArgumentType implements ArgumentType<class_2351> {
   public static final AxisArgumentType instance = new AxisArgumentType();
   public static final DynamicCommandExceptionType exceptionType = new DynamicCommandExceptionType(object -> Text.literal("Invalid Axis " + object));

   public static <S> class_2351 getAxis(CommandContext<S> context, String argName) {
      return (class_2351)context.getArgument(argName, class_2351.class);
   }

   public class_2351 parse(StringReader reader) throws CommandSyntaxException {
      String s = reader.readUnquotedString();

      return switch (s) {
         case "x", "X" -> class_2351.field_11048;
         case "y", "Y" -> class_2351.field_11052;
         case "z", "Z" -> class_2351.field_11051;
         default -> throw exceptionType.createWithContext(reader, s);
      };
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      return CommandSource.suggestMatching(Arrays.stream(class_2351.values()).map(Enum::name).collect(Collectors.toList()), builder);
   }

   public Collection<String> getExamples() {
      return Arrays.stream(class_2351.values()).map(Enum::toString).collect(Collectors.toList());
   }

   public static void init() {
      Platform.get().registerArgumentType(McHelper.newResourceLocation("imm_ptl:axis"), AxisArgumentType.class, ConstantArgumentSerializer.of(() -> instance));
   }
}
