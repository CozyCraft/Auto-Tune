package unprotesting.com.github.commands.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import lombok.NonNull;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import unprotesting.com.github.Main;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.config.Messages;
import unprotesting.com.github.data.ephemeral.data.AutosellData;
import unprotesting.com.github.data.ephemeral.other.Sale.SalePositionType;
import unprotesting.com.github.economy.EconomyFunctions;
import unprotesting.com.github.events.sync.UnlockUpdateEvent;
import unprotesting.com.github.util.UtilFunctions;

public class FunctionsUtil {

  public static void buyItem(@NotNull Player player, @NotNull ItemStack item) {

    double balance = EconomyFunctions.getEconomy().getBalance(player);
    double price = Main.getInstance().getCache().getItemPrice(item.getType().toString(), false);
    int amount = item.getAmount();
    double total = price * amount;
    String itemName = item.getType().toString();

    TagResolver tagResolver = TagResolver.resolver(
        Placeholder.component("item", item.displayName()),
        Placeholder.unparsed("price", UtilFunctions.getDf().format(price)),
        Placeholder.unparsed("amount", String.valueOf(amount)),
        Placeholder.unparsed("balance", UtilFunctions.getDf().format(balance)),
        Placeholder.unparsed("total", UtilFunctions.getDf().format(total)));

    if (price == 0) {

      Component message = Main.getInstance().getMm().deserialize(
          Messages.getMessages().getNotInShop(), tagResolver);

      player.sendMessage(message);

    }

    if (!UnlockUpdateEvent.isUnlocked(player, itemName)) {

      Component message = Main.getInstance().getMm().deserialize(
          Messages.getMessages().getNotUnlocked(), tagResolver);

      player.sendMessage(message);
      return;

    }

    if (balance < total) {

      player.sendMessage(Main.getInstance().getMm().deserialize(
          Messages.getMessages().getNotEnoughMoney(), tagResolver));
      
      amount = (int) Math.floor(balance / price);

      if (amount < 1) {
        return;
      }

    }

    if (Main.getInstance().getCache().getPurchasesLeft(itemName, player, true) < amount) {
  
      player.sendMessage(Main.getInstance().getMm().deserialize(
          Messages.getMessages().getRunOutOfBuys(), tagResolver));

      return;
    }

    HashMap<Integer, ItemStack> map = player.getInventory()
        .addItem(new ItemStack(Material.matchMaterial(itemName), amount));

    if ((map.size()) > 0) {

      ItemStack itemStack = (ItemStack) (Arrays.asList(map.values().toArray())).get(0);
      amount = amount - itemStack.getAmount();

    }

    if (amount < 1) {

      player.sendMessage(Main.getInstance().getMm().deserialize(
          Messages.getMessages().getNotEnoughSpace(), tagResolver));

      return;
    }

    total = price * amount;
    balance = EconomyFunctions.getEconomy().getBalance(player);

    tagResolver = TagResolver.resolver(
        Placeholder.component("item", item.displayName()),
        Placeholder.unparsed("price", UtilFunctions.getDf().format(price)),
        Placeholder.unparsed("amount", String.valueOf(amount)),
        Placeholder.unparsed("balance", UtilFunctions.getDf().format(balance)),
        Placeholder.unparsed("total", UtilFunctions.getDf().format(total)));

    EconomyFunctions.getEconomy().withdrawPlayer(player, total);

    player.sendMessage(Main.getInstance().getMm().deserialize(
        Messages.getMessages().getShopPurchase(), tagResolver));

    Main.getInstance().getCache().addSale(
        player.getUniqueId(), itemName, price, amount, SalePositionType.BUY);

  }

  public static void sellItem(@NotNull Player player, @NotNull ItemStack item) {
  
    double balance = EconomyFunctions.getEconomy().getBalance(player);
    double price = Main.getInstance().getCache().getItemPrice(item.getType().toString(), true);
    int amount = item.getAmount();
    double total = price * amount;
    String itemName = item.getType().toString();

    TagResolver tagResolver = TagResolver.resolver(
        Placeholder.component("item", item.displayName()),
        Placeholder.parsed("price", UtilFunctions.getDf().format(price)),
        Placeholder.parsed("amount", String.valueOf(amount)),
        Placeholder.parsed("balance", UtilFunctions.getDf().format(balance)),
        Placeholder.parsed("total", UtilFunctions.getDf().format(total)));

    if (price == 0) {
        
      Component message = Main.getInstance().getMm().deserialize(
          Messages.getMessages().getNotInShop(), tagResolver);
  
      player.sendMessage(message);
    }

    if (balance < total) {

      player.sendMessage(Main.getInstance().getMm().deserialize(
          Messages.getMessages().getNotEnoughMoney(), tagResolver));
      
      amount = (int) Math.floor(balance / price);

      if (amount < 1) {
        return;
      }

    }

    if (Main.getInstance().getCache().getPurchasesLeft(itemName, player, false) < amount) {
  
      player.sendMessage(Main.getInstance().getMm().deserialize(
          Messages.getMessages().getRunOutOfSells(), tagResolver));

      return;
    }

    HashMap<Integer, ItemStack> map = player.getInventory()
        .removeItem(new ItemStack(Material.matchMaterial(itemName), amount));

    if ((map.size()) > 0) {

      ItemStack itemStack = (ItemStack) (Arrays.asList(map.values().toArray())).get(0);
      amount = amount - itemStack.getAmount();

    }

    if (amount < 1) {

      player.sendMessage(Main.getInstance().getMm().deserialize(
          Messages.getMessages().getDoNotHaveItem(), tagResolver));

      return;
    }

    total = price * amount;
    balance = EconomyFunctions.getEconomy().getBalance(player);
    
    tagResolver = TagResolver.resolver(
        Placeholder.component("item", item.displayName()),
        Placeholder.parsed("price", UtilFunctions.getDf().format(price)),
        Placeholder.parsed("amount", String.valueOf(amount)),
        Placeholder.parsed("balance", UtilFunctions.getDf().format(balance)),
        Placeholder.parsed("total", UtilFunctions.getDf().format(total)));

    EconomyFunctions.getEconomy().depositPlayer(player, total);

    player.sendMessage(Main.getInstance().getMm().deserialize(
        Messages.getMessages().getShopSell(), tagResolver));

    Main.getInstance().getCache().addSale(
        player.getUniqueId(), itemName, price, amount, SalePositionType.SELL);
  }

  public static void buyEnchantment(Player player, @NonNull String enchantment) {

    Enchantment enchant = Enchantment.getByKey(NamespacedKey.minecraft(enchantment));

    if (!Config.getConfig().isEnableEnchantments()) {
      Main.getInstance().getLogger().info("Enchantments are disabled.");
    }

    ItemStack item = player.getInventory().getItemInMainHand();
    
    if (item == null) {

      player.sendMessage(Main.getInstance().getMm().deserialize(
          Messages.getMessages().getHoldItemInHand()));

      return;
    }

    int level = 0;
    String enchantmentName = enchantment;
    double balance = EconomyFunctions.getEconomy().getBalance(player);

    double itemPrice = Main.getInstance().getCache()
        .getItemPrice(item.getType().toString(), false);

    double price = Main.getInstance().getCache()
        .getOverallEnchantmentPrice(enchantment, itemPrice, false);

    TagResolver tagResolver = TagResolver.resolver(
        Placeholder.parsed("enchantment", enchantmentName),
        Placeholder.parsed("price", UtilFunctions.getDf().format(price)),
        Placeholder.parsed("balance", UtilFunctions.getDf().format(balance)),
        Placeholder.parsed("total", UtilFunctions.getDf().format(price)),
        Placeholder.component("item", item.displayName()));

    if (price == 0) {

      player.sendMessage(Main.getInstance().getMm().deserialize(
          Messages.getMessages().getNotInShop(), tagResolver));
    
    }
      
    if (item.containsEnchantment(enchant)) {

      level = item.getEnchantmentLevel(enchant);

    }

    if (balance < price) {

      player.sendMessage(Main.getInstance().getMm().deserialize(
          Messages.getMessages().getNotEnoughMoneyEnchantments(), tagResolver));
          
      return;
    }

    try {
      item.addEnchantment(enchant, level + 1);
    } catch (IllegalArgumentException e) {

      player.sendMessage(Main.getInstance().getMm().deserialize(
          Messages.getMessages().getEnchantmentError(), tagResolver));

      return;
    }

    EconomyFunctions.getEconomy().withdrawPlayer(player, price);
    
    Main.getInstance().getCache().addSale(player.getUniqueId(),
        enchantment, price, 1, SalePositionType.EBUY);

    player.sendMessage(Main.getInstance().getMm().deserialize(
        Messages.getMessages().getEnchantmentPurchase(), tagResolver));

    player.getInventory().setItemInMainHand(item);
  }

  public static void sellCustomItem(@NonNull Player player,
      @NonNull ItemStack item, boolean autosell) {

    Map<Enchantment, Integer> enchantments = item.getEnchantments();
    double itemPrice = Main.getInstance().getCache().getItemPrice(item.getType().toString(), true);
    int amount = item.getAmount();
    double total = itemPrice * amount;

    if (enchantments.size() > 0) {

      for (Enchantment enchantment : enchantments.keySet()) {

        double enchantmentPrice = Main.getInstance().getCache().getOverallEnchantmentPrice(
          enchantment.getKey().asString(), itemPrice, true) - itemPrice;

        total += enchantmentPrice * item.getEnchantmentLevel(enchantment);

      }

      total = getNewPriceWithDurability(total, item);
      total += itemPrice;
    }

    

    if (total == 0) {

      player.sendMessage(Main.getInstance().getMm().deserialize(
          Messages.getMessages().getNotInShop()));

    }

    if (item.getAmount() < 1) {

      if (!autosell) {

        player.sendMessage(Main.getInstance().getMm().deserialize(
            Messages.getMessages().getCannotSellCustom()));

        player.getInventory().addItem(item);

      }

      return;
    }

    double balance = EconomyFunctions.getEconomy().getBalance(player);

    TagResolver tagResolver = TagResolver.resolver(
        Placeholder.parsed("price", UtilFunctions.getDf().format(total)),
        Placeholder.parsed("balance", UtilFunctions.getDf().format(balance)),
        Placeholder.parsed("total", UtilFunctions.getDf().format(total)),
        Placeholder.component("item", item.displayName()),
        Placeholder.parsed("amount", String.valueOf(amount)));

    if (Main.getInstance().getCache().getPurchasesLeft(item.getType().toString(),
        player, false) < item.getAmount()) {
      
      player.sendMessage(Main.getInstance().getMm().deserialize(
          Messages.getMessages().getRunOutOfSells(), tagResolver));

      player.getInventory().addItem(item);
      return;

    }
    
    if (!autosell) {

      EconomyFunctions.getEconomy().depositPlayer(player, total);
      
      player.sendMessage(Main.getInstance().getMm().deserialize(
          Messages.getMessages().getSellCustomItem(), tagResolver));

    }

    if (autosell) {

      AutosellData data = Main.getInstance().getAutosellData();
      data.add(player.getUniqueId().toString(), total);
      Main.getInstance().setAutosellData(data);

    }

    Main.getInstance().getCache().addSale(player.getUniqueId(), item.getType().toString(),
        itemPrice, amount, SalePositionType.SELL);
    
    for (Enchantment enchantment : item.getEnchantments().keySet()) {

      Main.getInstance().getCache().addSale(player.getUniqueId(), enchantment.getKey().toString(),
          Main.getInstance().getCache().getEnchantmentPrice(enchantment.toString(), true), 
          item.getEnchantmentLevel(enchantment), SalePositionType.ESELL);

    }

  }

  @Deprecated
  private static double getNewPriceWithDurability(double price, ItemStack item) {

    if (!item.getItemMeta().isUnbreakable()) {
      return price;
    }

    double durability = (double) item.getDurability();
    double maxDurability = (double) item.getType().getMaxDurability();

    if (durability == 0) {
      return price;
    }

    double current = maxDurability - durability;
    double result = (current / maxDurability);
    double newPrice = price * result;
    newPrice = newPrice - newPrice * 0.01 * Config.getConfig().getDurabilityLimiter();
    return newPrice;

  }
}
