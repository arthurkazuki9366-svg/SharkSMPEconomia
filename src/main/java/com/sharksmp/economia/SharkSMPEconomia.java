package com.sharksmp.economia;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.*;

import java.util.*;

public class SharkSMPEconomia extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<UUID, Double> saldos = new HashMap<>();
    private final String TITULO_LOJA = ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Loja sharkSMP";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        carregarSaldos();

        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("shop").setExecutor(this);
        getCommand("sell").setExecutor(this);
        getCommand("bal").setExecutor(this);

        // Atualiza a Scoreboard lateral (HUD cinza/transparente padrao do Minecraft) a cada 2 segundos
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                atualizarScoreboard(p);
            }
        }, 20L, 40L);

        getLogger().info("SharkSMPEconomia ativado com sucesso!");
    }

    @Override
    public void onDisable() {
        salvarSaldos();
    }

    private void carregarSaldos() {
        if (getConfig().contains("jogadores")) {
            ConfigurationSection sec = getConfig().getConfigurationSection("jogadores");
            if (sec != null) {
                for (String key : sec.getKeys(false)) {
                    saldos.put(UUID.fromString(key), sec.getDouble(key));
                }
            }
        }
    }

    private void salvarSaldos() {
        for (Map.Entry<UUID, Double> entry : saldos.entrySet()) {
            getConfig().set("jogadores." + entry.getKey().toString(), entry.getValue());
        }
        saveConfig();
    }

    public double getSaldo(Player p) {
        return saldos.getOrDefault(p.getUniqueId(), getConfig().getDouble("servidor.dinheiro-inicial", 150.0));
    }

    public void setSaldo(Player p, double valor) {
        saldos.put(p.getUniqueId(), Math.max(0, valor));
        atualizarScoreboard(p);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!saldos.containsKey(p.getUniqueId())) {
            saldos.put(p.getUniqueId(), getConfig().getDouble("servidor.dinheiro-inicial", 150.0));
        }
        atualizarScoreboard(p);
    }

    // Scoreboard no canto direito da tela (fundo cinza semitransparente nativo do Minecraft)
    public void atualizarScoreboard(Player p) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;

        Scoreboard board = manager.getNewScoreboard();
        String nomeSv = ChatColor.translateAlternateColorCodes('&', getConfig().getString("servidor.nome", "&b&lsharkSMP"));
        Objective obj = board.registerNewObjective("sharksmp_hud", Criteria.DUMMY, nomeSv);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Calcula dias jogados no servidor (1 dia Minecraft = 24000 ticks = 20 minutos)
        int ticksJogados = p.getStatistic(Statistic.PLAY_ONE_MINUTE);
        int diasNoServidor = (ticksJogados / 24000) + 1;

        String discord = getConfig().getString("servidor.discord", "https://discord.gg/Rxs29HBPda");

        obj.getScore(ChatColor.GRAY + " ").setScore(7);
        obj.getScore(ChatColor.WHITE + "Dias no SV: " + ChatColor.AQUA + diasNoServidor + "d").setScore(6);
        obj.getScore(ChatColor.WHITE + "Saldo: " + ChatColor.GREEN + "$" + String.format("%.2f", getSaldo(p))).setScore(5);
        obj.getScore(ChatColor.GRAY + "  ").setScore(4);
        obj.getScore(ChatColor.YELLOW + "Comandos: /shop | /sell").setScore(3);
        obj.getScore(ChatColor.GRAY + "   ").setScore(2);
        obj.getScore(ChatColor.LIGHT_PURPLE + "Discord:").setScore(1);
        obj.getScore(ChatColor.WHITE + discord).setScore(0);

        p.setScoreboard(board);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Apenas jogadores podem usar este comando.");
            return true;
        }

        String cmd = command.getName().toLowerCase();

        if (cmd.equals("bal")) {
            p.sendMessage(ChatColor.AQUA + "[sharkSMP] " + ChatColor.WHITE + "Seu saldo atual: " + ChatColor.GREEN + "$" + String.format("%.2f", getSaldo(p)));
            return true;
        }

        if (cmd.equals("shop")) {
            abrirLoja(p);
            return true;
        }

        if (cmd.equals("sell")) {
            boolean venderTudo = args.length > 0 && args[0].equalsIgnoreCase("all");
            venderItens(p, venderTudo);
            return true;
        }

        return false;
    }

    private void abrirLoja(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, TITULO_LOJA);
        ConfigurationSection itensSec = getConfig().getConfigurationSection("itens");

        if (itensSec != null) {
            int slot = 0;
            for (String matName : itensSec.getKeys(false)) {
                if (slot >= 26) break;
                Material mat = Material.matchMaterial(matName);
                if (mat == null) continue;

                String nome = ChatColor.translateAlternateColorCodes('&', itensSec.getString(matName + ".nome", matName));
                double compra = itensSec.getDouble(matName + ".compra");
                double venda = itensSec.getDouble(matName + ".venda");

                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(nome);
                    List<String> lore = new ArrayList<>();
                    lore.add(ChatColor.GREEN + "Comprar (Clique Esq.): $" + compra);
                    lore.add(ChatColor.GOLD + "Valor de venda: $" + venda);
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
                inv.setItem(slot++, item);
            }
        }

        // Botao de Vender Tudo no slot 26
        ItemStack btnVender = new ItemStack(Material.EMERALD);
        ItemMeta metaVender = btnVender.getItemMeta();
        if (metaVender != null) {
            metaVender.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "VENDER TUDO (/sell all)");
            metaVender.setLore(Collections.singletonList(ChatColor.GRAY + "Clique para vender todos os itens vendaveis do inventario"));
            btnVender.setItemMeta(metaVender);
        }
        inv.setItem(26, btnVender);

        p.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!e.getView().getTitle().equals(TITULO_LOJA)) return;

        e.setCancelled(true);
        ItemStack clicado = e.getCurrentItem();
        if (clicado == null || clicado.getType() == Material.AIR) return;

        if (e.getSlot() == 26 && clicado.getType() == Material.EMERALD) {
            venderItens(p, true);
            return;
        }

        String matName = clicado.getType().name();
        if (getConfig().contains("itens." + matName)) {
            double precoCompra = getConfig().getDouble("itens." + matName + ".compra");
            double saldoAtual = getSaldo(p);

            if (saldoAtual >= precoCompra) {
                setSaldo(p, saldoAtual - precoCompra);
                p.getInventory().addItem(new ItemStack(clicado.getType(), 1));
                p.sendMessage(ChatColor.AQUA + "[sharkSMP] " + ChatColor.GREEN + "Voce comprou 1x " + matName + " por $" + precoCompra);
            } else {
                p.sendMessage(ChatColor.AQUA + "[sharkSMP] " + ChatColor.RED + "Saldo insuficiente! Voce precisa de $" + precoCompra);
            }
        }
    }

    private void venderItens(Player p, boolean tudo) {
        ConfigurationSection itensSec = getConfig().getConfigurationSection("itens");
        if (itensSec == null) return;

        double totalGanho = 0;
        int totalItens = 0;

        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack item = p.getInventory().getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;

            String matName = item.getType().name();
            if (itensSec.contains(matName)) {
                double precoVenda = itensSec.getDouble(matName + ".venda");
                int qtd = item.getAmount();

                if (tudo) {
                    totalGanho += precoVenda * qtd;
                    totalItens += qtd;
                    p.getInventory().setItem(i, null);
                } else {
                    totalGanho += precoVenda;
                    totalItens += 1;
                    if (qtd > 1) {
                        item.setAmount(qtd - 1);
                    } else {
                        p.getInventory().setItem(i, null);
                    }
                    break;
                }
            }
        }

        if (totalItens > 0) {
            setSaldo(p, getSaldo(p) + totalGanho);
            p.sendMessage(ChatColor.AQUA + "[sharkSMP] " + ChatColor.GREEN + "Voce vendeu " + totalItens + " item(ns) por $" + String.format("%.2f", totalGanho) + "!");
        } else {
            p.sendMessage(ChatColor.AQUA + "[sharkSMP] " + ChatColor.RED + "Voce nao tem itens vendaveis no inventario.");
        }
    }
}
