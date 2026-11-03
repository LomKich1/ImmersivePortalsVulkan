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
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.animation.TimingFunction;
import qouteall.q_misc_util.platform.Platform;

public class TimingFunctionArgumentType implements ArgumentType<TimingFunction> {
   public static final TimingFunctionArgumentType instance = new TimingFunctionArgumentType();
   public static final DynamicCommandExceptionType exceptionType = new DynamicCommandExceptionType(object -> Text.literal("Invalid Timing Function " + object));

   public static TimingFunction get(CommandContext<?> context, String argName) {
      return (TimingFunction)context.getArgument(argName, TimingFunction.class);
   }

   public TimingFunction parse(StringReader reader) throws CommandSyntaxException {
      String s = reader.readUnquotedString();
      return TimingFunction.valueOf(s);
   }

   public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
      return CommandSource.suggestMatching(Arrays.stream(TimingFunction.values()).map(Enum::name).collect(Collectors.toList()), builder);
   }

   public Collection<String> getExamples() {
      return Arrays.stream(TimingFunction.values()).map(Enum::toString).collect(Collectors.toList());
   }

   public static void init() {
      Platform.get()
         .registerArgumentType(
            McHelper.newResourceLocation("imm_ptl:timing_function_argument_type"),
            TimingFunctionArgumentType.class,
            ConstantArgumentSerializer.of(() -> instance)
         );
   }
}
