package com.palmergames.bukkit.towny.object.economy;

import com.palmergames.bukkit.towny.TownyEconomyHandler;
import com.palmergames.bukkit.towny.TownyMessaging;
import com.palmergames.bukkit.towny.TownySettings;
import com.palmergames.bukkit.towny.exceptions.EconomyException;
import com.palmergames.bukkit.towny.object.EconomyAccount;
import org.bukkit.World;

/**
 * A variant of an account that implements
 * a checked cap on it's balance, as well as a 
 * debt system.
 */
public class BankAccount extends Account {
	
	private double balanceCap;
	private final Account debtAccount = new DebtAccount(this);
	private double debtCap;

	/**
	 * Because of limitations in Economy API's, debt isn't
	 * supported reliably in them so we need use another account
	 * as a workaround for this problem.
	 */
	static class DebtAccount extends EconomyAccount {
		
		public static final String DEBT_PREFIX = TownySettings.getDebtAccountPrefix();

		public DebtAccount(Account account) {
			super(account.getName() + DEBT_PREFIX, account.getBukkitWorld());
		}
	}
	
	public BankAccount(String name, World world, double balanceCap) {
		super(name, world);
		this.balanceCap = balanceCap;
	}

	/**
	 * Sets the max amount of money allowed in this account.
	 * 
	 * @param balanceCap The max amount allowed in this account.
	 */
	public void setBalanceCap(double balanceCap) {
		this.balanceCap = balanceCap;
	}

	/**
	 * Sets the maximum amount of money this account can have.
	 *
	 * @return the max amount allowed in this account.
	 */
	public double getBalanceCap() {
		return balanceCap;
	}

	/**
	 * The maximum amount of debt this account can have.
	 * 
	 * @return The max amount of debt for this account.
	 */
	public double getDebtCap() {
		return debtCap;
	}

	/**
	 * Sets the maximum amount of debt this account can have.
	 * 
	 * @param debtCap The new cap for debt on this account.
	 */
	public void setDebtCap(double debtCap) {
		this.debtCap = debtCap;
	}

	@Override
	protected boolean subtractMoney(double amount) {
		try {
			// Check cap
			if (isBankrupt() && (debtAccount.getHoldingBalance() + amount > getDebtCap())) {
				TownyMessaging.sendErrorMsg("got here");
				return false;
			}
			
			if (isBankrupt()) {
				return addDebt(amount);
			}
			
			if (!canPayFromHoldings(amount)) {
				
				// Calculate debt.
				double amountInDebt = amount - getHoldingBalance();
				
				TownyMessaging.sendErrorMsg("amount = " + amountInDebt);

				// Empty out account.
				boolean success = TownyEconomyHandler.setBalance(getName(), 0, world);
				success &= addDebt(amountInDebt);
				
				return success;
			}
		} catch (EconomyException e) {
			e.printStackTrace();
		}
		
		// Otherwise continue like normal.
		return TownyEconomyHandler.subtract(getName(), amount, world);
	}

	@Override
	protected boolean addMoney(double amount) {
		try {
			
			// Check balance cap.
			if (balanceCap != 0 && !(getHoldingBalance() + amount > balanceCap)) {
				return false;
			}
			
			if (isBankrupt()) {
				return removeDebt(amount);
			}
		} catch (EconomyException e) {
			e.printStackTrace();
		}

		// Otherwise continue like normal.
		return TownyEconomyHandler.add(getName(), amount, world);
	}

	/**
	 * Whether the account is in debt or not.
	 * 
	 * @return true if in debt, false otherwise.
	 * @throws EconomyException On an economy error.
	 */
	public boolean isBankrupt() throws EconomyException {
		return debtAccount.getHoldingBalance() > 0;
	}
	
	private boolean addDebt(double amount) throws EconomyException {
		return debtAccount.deposit(amount, null);
	}
	
	private boolean removeDebt(double amount) throws EconomyException {
		if (!debtAccount.canPayFromHoldings(amount)) {
			
			// Calculate money being added.
			double netMoney = amount - debtAccount.getHoldingBalance();
			
			// Zero out balance
			TownyEconomyHandler.setBalance(debtAccount.getName(), 0, world);
			
			return deposit(netMoney, null);
		}
		
		return TownyEconomyHandler.subtract(debtAccount.getName(), amount, getBukkitWorld());
	}

	@Override
	public double getHoldingBalance() throws EconomyException {
		try {
			if (isBankrupt()) {
				return TownyEconomyHandler.getBalance(debtAccount.getName(), getBukkitWorld()) * -1;
			}
			return TownyEconomyHandler.getBalance(getName(), getBukkitWorld());
		} catch (NoClassDefFoundError e) {
			e.printStackTrace();
			throw new EconomyException("Economy error getting holdings for " + getName());
		}
	}

	@Override
	public String getHoldingFormattedBalance() {
		try {
			if (isBankrupt()) {
				return "-" + debtAccount.getHoldingFormattedBalance();
			}
			return TownyEconomyHandler.getFormattedBalance(getHoldingBalance());
		} catch (EconomyException e) {
			return "Error";
		}
	}

	@Override
	public void removeAccount() {
		// Make sure to remove debt account
		TownyEconomyHandler.removeAccount(debtAccount.getName());
		TownyEconomyHandler.removeAccount(getName());
	}
}
