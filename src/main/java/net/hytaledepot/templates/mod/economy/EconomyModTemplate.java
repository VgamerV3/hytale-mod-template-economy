package net.hytaledepot.templates.mod.economy;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

public final class EconomyModTemplate {
  private final Map<String, AtomicLong> actionCounters = new ConcurrentHashMap<>();
  private final Map<String, String> lastActionBySender = new ConcurrentHashMap<>();
  private final AtomicBoolean demoFlagEnabled = new AtomicBoolean(false);
  private final AtomicLong errorCount = new AtomicLong();
  Map<String, AtomicLong> balances = new ConcurrentHashMap<>();
  Deque<String> ledger = new ArrayDeque<>();
  private volatile Path dataDirectory;

  public void onInitialize(Path dataDirectory) {
    this.dataDirectory = dataDirectory;
    balances.computeIfAbsent("treasury", key -> new AtomicLong(500));
  }

  public void onShutdown() {
    ledger.clear();
  }

  public void onHeartbeat(long tick) {
    actionCounters.computeIfAbsent("heartbeat", key -> new AtomicLong()).incrementAndGet();

  }

  public String runAction(String sender, String action, long heartbeatTicks) {
    String normalizedSender = String.valueOf(sender == null ? "unknown" : sender);
    String normalizedAction = normalizeAction(action);

    actionCounters.computeIfAbsent(normalizedAction, key -> new AtomicLong()).incrementAndGet();
    lastActionBySender.put(normalizedSender, normalizedAction);

    if ("toggle".equals(normalizedAction)) {
      boolean enabled = toggleFlag(demoFlagEnabled);
      return "[EconomyMod] demoFlag=" + enabled + ", heartbeatTicks=" + heartbeatTicks;
    }

    if ("info".equals(normalizedAction)) {
      return "[EconomyMod] " + diagnostics(normalizedSender, heartbeatTicks);
    }

    String domainResult = handleDomainAction(normalizedSender, normalizedAction, heartbeatTicks);
    if (domainResult != null) {
      return "[EconomyMod] " + domainResult;
    }

    return "[EconomyMod] unknown action='" + normalizedAction + "' (try: info, toggle, sample, credit-demo, transfer-demo, balance-demo)";
  }

  public String diagnostics(String sender, long heartbeatTicks) {
    String directory = dataDirectory == null ? "unset" : dataDirectory.toString();
    return "sender=" + sender
        + ", heartbeatTicks=" + heartbeatTicks
        + ", demoFlag=" + demoFlagEnabled.get()
        + ", ops=" + operationCount()
        + ", lastAction=" + lastActionBySender.getOrDefault(sender, "none")
        + ", errors=" + errorCount.get()
        + ", accounts=" + balances.size() + ", ledgerEntries=" + ledger.size() + ", treasury=" + balanceOf("treasury") + ", dataDirectory=" + directory;
  }

  public long operationCount() {
    long total = 0;
    for (AtomicLong value : actionCounters.values()) {
      total += value.get();
    }
    return total;
  }

  public void incrementErrorCount() {
    errorCount.incrementAndGet();
  }

  private String handleDomainAction(String sender, String action, long heartbeatTicks) {
    if ("sample".equals(action) || "credit-demo".equals(action)) {
      long balance = balanceRef(sender).addAndGet(25);
      appendLedger("credit", sender, 25, balance);
      return "credited 25 coins, balance=" + balance;
    }
    if ("transfer-demo".equals(action)) {
      AtomicLong senderBalance = balanceRef(sender);
      if (senderBalance.get() < 10) {
        return "transfer blocked, balance=" + senderBalance.get() + " (need >=10)";
      }
      long nextSender = senderBalance.addAndGet(-10);
      long treasury = balanceRef("treasury").addAndGet(10);
      appendLedger("transfer", sender, 10, nextSender);
      return "transferred 10 to treasury, senderBalance=" + nextSender + ", treasury=" + treasury;
    }
    if ("balance-demo".equals(action)) {
      return "balance=" + balanceOf(sender) + ", treasury=" + balanceOf("treasury");
    }
    return null;
  }

  private AtomicLong balanceRef(String account) {
    return balances.computeIfAbsent(String.valueOf(account).toLowerCase(), key -> new AtomicLong());
  }

  private long balanceOf(String account) {
    return balanceRef(account).get();
  }

  private void appendLedger(String kind, String account, long amount, long resultingBalance) {
    ledger.addLast(kind + ":" + account + ":" + amount + ":" + resultingBalance);
    while (ledger.size() > 24) {
      ledger.removeFirst();
    }
  }

  private static String normalizeAction(String action) {
    String normalized = String.valueOf(action == null ? "" : action).trim().toLowerCase();
    return normalized.isEmpty() ? "sample" : normalized;
  }

  private static boolean toggleFlag(AtomicBoolean flag) {
    while (true) {
      boolean current = flag.get();
      boolean next = !current;
      if (flag.compareAndSet(current, next)) {
        return next;
      }
    }
  }
}
