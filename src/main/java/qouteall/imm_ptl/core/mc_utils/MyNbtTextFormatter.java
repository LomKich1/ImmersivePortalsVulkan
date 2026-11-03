package qouteall.imm_ptl.core.mc_utils;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.bytes.ByteCollection;
import it.unimi.dsi.fastutil.bytes.ByteOpenHashSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.util.Formatting;
import net.minecraft.nbt.NbtByteArray;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtEnd;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtIntArray;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLongArray;
import net.minecraft.nbt.NbtLong;
import net.minecraft.nbt.NbtShort;
import net.minecraft.nbt.NbtString;
import net.minecraft.nbt.NbtElement;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.nbt.visitor.NbtElementVisitor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;

@IPVanillaCopy
public class MyNbtTextFormatter implements NbtElementVisitor {
   private static final Logger LOGGER = LogManager.getLogger();
   private static final int field_33271 = 8;
   private static final ByteCollection SINGLE_LINE_ELEMENT_TYPES = new ByteOpenHashSet(Arrays.asList((byte)1, (byte)2, (byte)3, (byte)4, (byte)5, (byte)6));
   private static final Formatting NAME_COLOR = Formatting.field_1075;
   private static final Formatting STRING_COLOR = Formatting.field_1060;
   private static final Formatting NUMBER_COLOR = Formatting.field_1065;
   private static final Formatting TYPE_SUFFIX_COLOR = Formatting.field_1061;
   private static final Pattern SIMPLE_NAME = Pattern.compile("[A-Za-z0-9._+-]+");
   private static final String KEY_VALUE_SEPARATOR = String.valueOf(':');
   private static final String ENTRY_SEPARATOR = String.valueOf(',');
   private static final String SQUARE_OPEN_BRACKET = "[";
   private static final String SQUARE_CLOSE_BRACKET = "]";
   private static final String SEMICOLON = ";";
   private static final String SPACE = " ";
   private static final String CURLY_OPEN_BRACKET = "{";
   private static final String CURLY_CLOSE_BRACKET = "}";
   private static final String NEW_LINE = "\n";
   private final String prefix;
   private final int indentationLevel;
   private Text result = Text.literal("");

   public MyNbtTextFormatter(String prefix, int indentationLevel) {
      this.prefix = prefix;
      this.indentationLevel = indentationLevel;
   }

   public Text apply(NbtElement element) {
      element.accept(this);
      return this.result;
   }

   public void visitString(NbtString element) {
      String string = NbtString.escape(element.comp_3831());
      String string2 = string.substring(0, 1);
      MutableText text = Text.literal(string.substring(1, string.length() - 1)).formatted(STRING_COLOR);
      this.result = Text.literal(string2).append(text).append(string2);
   }

   public void visitByte(NbtByte element) {
      if (element.comp_3817() == 0) {
         this.result = Text.literal("false").formatted(NUMBER_COLOR);
      } else if (element.comp_3817() == 1) {
         this.result = Text.literal("true").formatted(NUMBER_COLOR);
      } else {
         MutableText text = Text.literal("b").formatted(TYPE_SUFFIX_COLOR);
         this.result = Text.literal(String.valueOf(element.comp_3817())).append(text).formatted(NUMBER_COLOR);
      }
   }

   public void visitShort(NbtShort element) {
      MutableText text = Text.literal("s").formatted(TYPE_SUFFIX_COLOR);
      this.result = Text.literal(String.valueOf(element.comp_3822())).append(text).formatted(NUMBER_COLOR);
   }

   public void visitInt(NbtInt element) {
      this.result = Text.literal(String.valueOf(element.comp_3820())).formatted(NUMBER_COLOR);
   }

   public void visitLong(NbtLong element) {
      MutableText text = Text.literal("L").formatted(TYPE_SUFFIX_COLOR);
      this.result = Text.literal(String.valueOf(element.comp_3821())).append(text).formatted(NUMBER_COLOR);
   }

   public void visitFloat(NbtFloat element) {
      MutableText text = Text.literal("f").formatted(TYPE_SUFFIX_COLOR);
      this.result = Text.literal(String.valueOf(element.comp_3819())).append(text).formatted(NUMBER_COLOR);
   }

   public void visitDouble(NbtDouble element) {
      MutableText text = Text.literal("d").formatted(TYPE_SUFFIX_COLOR);
      this.result = Text.literal(String.valueOf(element.comp_3818())).append(text).formatted(NUMBER_COLOR);
   }

   public void visitByteArray(NbtByteArray element) {
      MutableText text = Text.literal("B").formatted(TYPE_SUFFIX_COLOR);
      MutableText mutableText = Text.literal("[").append(text).append(";");
      byte[] bs = element.getByteArray();

      for (int i = 0; i < bs.length; i++) {
         MutableText mutableText2 = Text.literal(String.valueOf(bs[i])).formatted(NUMBER_COLOR);
         mutableText.append(" ").append(mutableText2).append(text);
         if (i != bs.length - 1) {
            mutableText.append(ENTRY_SEPARATOR);
         }
      }

      mutableText.append("]");
      this.result = mutableText;
   }

   public void visitIntArray(NbtIntArray element) {
      MutableText text = Text.literal("I").formatted(TYPE_SUFFIX_COLOR);
      MutableText mutableText = Text.literal("[").append(text).append(";");
      int[] is = element.getIntArray();

      for (int i = 0; i < is.length; i++) {
         mutableText.append(" ").append(Text.literal(String.valueOf(is[i])).formatted(NUMBER_COLOR));
         if (i != is.length - 1) {
            mutableText.append(ENTRY_SEPARATOR);
         }
      }

      mutableText.append("]");
      this.result = mutableText;
   }

   public void visitLongArray(NbtLongArray element) {
      MutableText text = Text.literal("L").formatted(TYPE_SUFFIX_COLOR);
      MutableText mutableText = Text.literal("[").append(text).append(";");
      long[] ls = element.getLongArray();

      for (int i = 0; i < ls.length; i++) {
         MutableText text2 = Text.literal(String.valueOf(ls[i])).formatted(NUMBER_COLOR);
         mutableText.append(" ").append(text2).append(text);
         if (i != ls.length - 1) {
            mutableText.append(ENTRY_SEPARATOR);
         }
      }

      mutableText.append("]");
      this.result = mutableText;
   }

   public void visitList(NbtList element) {
      if (element.isEmpty()) {
         this.result = Text.literal("[]");
      } else if (element.size() <= 8 && element.stream().allMatch(t -> SINGLE_LINE_ELEMENT_TYPES.contains(t.getType()))) {
         String string = ENTRY_SEPARATOR + " ";
         MutableText mutableText = Text.literal("[");

         for (int i = 0; i < element.size(); i++) {
            if (i != 0) {
               mutableText.append(string);
            }

            mutableText.append(new MyNbtTextFormatter(this.prefix, this.indentationLevel).apply(element.method_10534(i)));
         }

         mutableText.append("]");
         this.result = mutableText;
      } else {
         MutableText string = Text.literal("[");

         for (int mutableText = 0; mutableText < element.size(); mutableText++) {
            MutableText i = Text.literal(Strings.repeat(this.prefix, this.indentationLevel + 1));
            i.append(new MyNbtTextFormatter(this.prefix, this.indentationLevel + 1).apply(element.method_10534(mutableText)));
            if (mutableText != element.size() - 1) {
               i.append(ENTRY_SEPARATOR).append(" ");
            }

            string.append(i);
         }

         if (!this.prefix.isEmpty()) {
            string.append("\n").append(Strings.repeat(this.prefix, this.indentationLevel));
         }

         string.append("]");
         this.result = string;
      }
   }

   public void visitCompound(NbtCompound compound) {
      if (compound.isEmpty()) {
         this.result = Text.literal("{}");
      } else {
         MutableText mutableText = Text.literal("{");
         Collection<String> collection = compound.getKeys();
         List<String> list = Lists.newArrayList(compound.getKeys());
         Collections.sort(list);
         if (!this.prefix.isEmpty()) {
            mutableText.append("\n");
         }

         Iterator<String> iterator = list.iterator();

         while (iterator.hasNext()) {
            String string = iterator.next();
            MutableText mutableText2 = Text.literal(Strings.repeat(this.prefix, this.indentationLevel + 1))
               .append(escapeName(string))
               .append(KEY_VALUE_SEPARATOR)
               .append(" ")
               .append(new MyNbtTextFormatter(this.prefix, this.indentationLevel + 1).apply(compound.get(string)));
            if (iterator.hasNext()) {
               mutableText2.append(ENTRY_SEPARATOR).append(this.prefix.isEmpty() ? " " : "\n");
            }

            mutableText.append(mutableText2);
         }

         if (!this.prefix.isEmpty()) {
            mutableText.append("\n").append(Strings.repeat(this.prefix, this.indentationLevel));
         }

         mutableText.append("}");
         this.result = mutableText;
      }
   }

   protected static Text escapeName(String name) {
      if (SIMPLE_NAME.matcher(name).matches()) {
         return Text.literal(name).formatted(NAME_COLOR);
      } else {
         String string = NbtString.escape(name);
         String string2 = string.substring(0, 1);
         MutableText text = Text.literal(string.substring(1, string.length() - 1)).formatted(NAME_COLOR);
         return Text.literal(string2).append(text).append(string2);
      }
   }

   public void visitEnd(NbtEnd element) {
   }
}
