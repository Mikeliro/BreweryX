package com.dre.brewery.recipe;

import com.dre.brewery.BIngredients;
import com.dre.brewery.Brew;
import com.dre.brewery.BreweryPlugin;
import com.dre.brewery.filedata.BConfig;
import com.dre.brewery.utility.BUtil;
import com.dre.brewery.utility.LegacyUtil;
import com.dre.brewery.utility.MinecraftVersion;
import com.dre.brewery.utility.StringParser;
import com.dre.brewery.utility.Tuple;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Recipe used to Brew a Brewery Potion.
 */
public class BRecipe implements Cloneable {

	private static final List<BRecipe> recipes = new ArrayList<>();
	public static int numConfigRecipes; // The number of recipes in the list that are from config

	// info
	private String[] name;
	private boolean saveInData; // If this recipe should be saved in data and loaded again when the server restarts. Applicable to non-config recipes
	private String id; // ID that might be given by the config

	// brewing
	private List<RecipeItem> ingredients = new ArrayList<>(); // Items and amounts
	private int difficulty; // difficulty to brew the potion, how exact the instruction has to be followed
	private int cookingTime; // time to cook in cauldron
	private byte distillruns; // runs through the brewer
	private int distillTime; // time for one distill run in seconds
	private byte wood; // type of wood the barrel has to consist of
	private int age; // time in minecraft days for the potions to age in barrels

	// outcome
	private PotionColor color; // color of the distilled/finished potion
	private int alcohol; // Alcohol in perfect potion
	private Map<Integer, String> lore; // Custom Lore on the Potion. The int is for Quality Lore, 0 = any, 1,2,3 = Bad,Middle,Good
	private int[] cmData; // Custom Model Data[3] for each quality

	// drinking
	private List<BEffect> effects = new ArrayList<>(); // Special Effects when drinking
	private @Nullable Map<Integer, String> playercmds; // Commands executed as the player when drinking
	private @Nullable Map<Integer, String> servercmds; // Commands executed as the server when drinking
	private String drinkMsg; // Message when drinking
	private String drinkTitle; // Title to show when drinking
	private boolean glint; // If the potion should have a glint effect

	public BRecipe() {
	}

	/**
	 * New BRecipe with Name.
	 * <p>Use new BRecipe.Builder() for easier Recipe Creation
	 *
	 * @param name The name for all qualities
	 */
	public BRecipe(String name, @NotNull PotionColor color) {
		this.name = new String[] {name};
		this.color = color;
		difficulty = 5;
	}

	/**
	 * New BRecipe with Names.
	 * <p>Use new BRecipe.Builder() for easier Recipe Creation
	 *
	 * @param names {name bad, name normal, name good}
	 */
	public BRecipe(String[] names, @NotNull PotionColor color) {
		this.name = names;
		this.color = color;
		difficulty = 5;
	}

	@Nullable
	public static BRecipe fromConfig(ConfigurationSection configSectionRecipes, String recipeId) {
		BRecipe recipe = new BRecipe();
		recipe.id = recipeId;
		String nameList = configSectionRecipes.getString(recipeId + ".name");
		if (nameList != null) {
			String[] name = nameList.split("/");
			if (name.length > 2) {
				recipe.name = name;
			} else {
				recipe.name = new String[1];
				recipe.name[0] = name[0];
			}
		} else {
			BreweryPlugin.getInstance().errorLog(recipeId + ": Recipe Name missing or invalid!");
			return null;
		}
		if (recipe.getRecipeName() == null || recipe.getRecipeName().isEmpty()) {
			BreweryPlugin.getInstance().errorLog(recipeId + ": Recipe Name invalid");
			return null;
		}

		recipe.ingredients = loadIngredients(configSectionRecipes, recipeId);
		if (recipe.ingredients == null || recipe.ingredients.isEmpty()) {
			BreweryPlugin.getInstance().errorLog("No ingredients for: " + recipe.getRecipeName());
			return null;
		}
		recipe.cookingTime = configSectionRecipes.getInt(recipeId + ".cookingtime", 1);
		int dis = configSectionRecipes.getInt(recipeId + ".distillruns", 0);
		if (dis > Byte.MAX_VALUE) {
			recipe.distillruns = Byte.MAX_VALUE;
		} else {
			recipe.distillruns = (byte) dis;
		}
		recipe.distillTime = configSectionRecipes.getInt(recipeId + ".distilltime", 0) * 20;
		recipe.wood = (byte) configSectionRecipes.getInt(recipeId + ".wood", 0);
		recipe.age = configSectionRecipes.getInt(recipeId + ".age", 0);
		recipe.difficulty = configSectionRecipes.getInt(recipeId + ".difficulty", 0);
		recipe.alcohol = configSectionRecipes.getInt(recipeId + ".alcohol", 0);

		String col = configSectionRecipes.getString(recipeId + ".color", "BLUE");
		recipe.color = PotionColor.fromString(col);
		if (recipe.color == PotionColor.WATER && !col.equals("WATER")) {
			BreweryPlugin.getInstance().errorLog("Invalid Color '" + col + "' in Recipe: " + recipe.getRecipeName());
			return null;
		}

		recipe.lore = loadQualityStringList(configSectionRecipes, recipeId + ".lore", StringParser.ParseType.LORE);

		recipe.servercmds = loadQualityStringList(configSectionRecipes, recipeId + ".servercommands", StringParser.ParseType.CMD);
		recipe.playercmds = loadQualityStringList(configSectionRecipes, recipeId + ".playercommands", StringParser.ParseType.CMD);

		recipe.drinkMsg = BreweryPlugin.getInstance().color(BUtil.loadCfgString(configSectionRecipes, recipeId + ".drinkmessage"));
		recipe.drinkTitle = BreweryPlugin.getInstance().color(BUtil.loadCfgString(configSectionRecipes, recipeId + ".drinktitle"));
		recipe.glint = configSectionRecipes.getBoolean(recipeId + ".glint", false);

		if (configSectionRecipes.isString(recipeId + ".customModelData")) {
			String[] cmdParts = configSectionRecipes.getString(recipeId + ".customModelData", "").split("/");
			if (cmdParts.length == 3) {
				recipe.cmData = new int[] {BreweryPlugin.getInstance().parseInt(cmdParts[0]), BreweryPlugin.getInstance().parseInt(cmdParts[1]), BreweryPlugin.getInstance().parseInt(cmdParts[2])};
				if (recipe.cmData[0] == 0 && recipe.cmData[1] == 0 && recipe.cmData[2] == 0) {
					BreweryPlugin.getInstance().errorLog("Invalid customModelData in Recipe: " + recipe.getRecipeName());
					recipe.cmData = null;
				}
			} else {
				BreweryPlugin.getInstance().errorLog("Invalid customModelData in Recipe: " + recipe.getRecipeName());
			}
		} else {
			int cmd = configSectionRecipes.getInt(recipeId + ".customModelData", 0);
			if (cmd != 0) {
				recipe.cmData = new int[] {cmd, cmd, cmd};
			}
		}

		List<String> effectStringList = configSectionRecipes.getStringList(recipeId + ".effects");
        for (String effectString : effectStringList) {
            BEffect effect = new BEffect(effectString);
            if (effect.isValid()) {
                recipe.effects.add(effect);
            } else {
                BreweryPlugin.getInstance().errorLog("Error adding Effect to Recipe: " + recipe.getRecipeName());
            }
        }
        return recipe;
	}

	public static List<RecipeItem> loadIngredients(ConfigurationSection cfg, String recipeId) {
		List<String> ingredientsList;
		if (cfg.isString(recipeId + ".ingredients")) {
			ingredientsList = new ArrayList<>(1);
			ingredientsList.add(cfg.getString(recipeId + ".ingredients", "x"));
		} else {
			ingredientsList = cfg.getStringList(recipeId + ".ingredients");
		}
		return loadIngredients(ingredientsList, recipeId);
	}

	public static List<RecipeItem> loadIngredients(List<String> stringList, String recipeId) {
        List<RecipeItem> ingredients = new ArrayList<>(stringList.size());

		listLoop: for (String item : stringList) {
			String[] ingredParts = item.split("/");
			int amount = 1;
			if (ingredParts.length == 2) {
				amount = BreweryPlugin.getInstance().parseInt(ingredParts[1]);
				if (amount < 1) {
					BreweryPlugin.getInstance().errorLog(recipeId + ": Invalid Item Amount: " + ingredParts[1]);
					return null;
				}
			}
			String[] matParts;
			if (ingredParts[0].contains(",")) {
				matParts = ingredParts[0].split(",");
			} else if (ingredParts[0].contains(";")) {
				matParts = ingredParts[0].split(";");
			} else {
				matParts = ingredParts[0].split("\\.");
			}

			if (BreweryPlugin.getMCVersion().isOrEarlier(MinecraftVersion.V1_14) && matParts[0].equalsIgnoreCase("sweet_berries")) {
				// Using this in default recipes, but will error on < 1.14
				ingredients.add(new SimpleItem(Material.BEDROCK));
				continue;
			}

			// Check if this is a Plugin Item
			String[] pluginItem = matParts[0].split(":");
			if (pluginItem.length > 1) {
				RecipeItem custom = com.dre.brewery.recipe.PluginItem.fromConfig(pluginItem[0], pluginItem[1]);
				if (custom != null) {
					custom.setAmount(amount);
					custom.makeImmutable();
					ingredients.add(custom);
					BCauldronRecipe.acceptedCustom.add(custom);
					continue;
				} else {
					// TODO Maybe load later ie on first use of recipe?
					BreweryPlugin.getInstance().errorLog(recipeId + ": Could not Find Plugin: " + ingredParts[1]);
					return null;
				}
			}

			// Try to find this Ingredient as Custom Item
			for (RecipeItem custom : BConfig.customItems) {
				if (custom.getConfigId().equalsIgnoreCase(matParts[0])) {
					custom = custom.getMutableCopy();
					custom.setAmount(amount);
					custom.makeImmutable();
					ingredients.add(custom);
					if (custom.hasMaterials()) {
						BCauldronRecipe.acceptedMaterials.addAll(custom.getMaterials());
					}
					// Add it as acceptedCustom
					if (!BCauldronRecipe.acceptedCustom.contains(custom)) {
						BCauldronRecipe.acceptedCustom.add(custom);
					}
					continue listLoop;
				}
			}

			Material mat = BUtil.getMaterialSafely(matParts[0]);
			short durability = -1;
			if (matParts.length == 2) {
				durability = (short) BreweryPlugin.getInstance().parseInt(matParts[1]);
			}
			if (mat == null && BConfig.hasVault) {
				try {
					net.milkbowl.vault.item.ItemInfo vaultItem = net.milkbowl.vault.item.Items.itemByString(matParts[0]);
					if (vaultItem != null) {
						mat = vaultItem.getType();
						if (durability == -1 && vaultItem.getSubTypeId() != 0) {
							durability = vaultItem.getSubTypeId();
						}
						if (mat.name().contains("LEAVES")) {
							if (durability > 3) {
								durability -= 4; // Vault has leaves with higher durability
							}
						}
					}
				} catch (Throwable e) {
					BreweryPlugin.getInstance().errorLog("Could not check vault for Item Name");
					e.printStackTrace();
				}
			}
			if (mat != null) {
				RecipeItem rItem;
				if (durability > -1) {
					rItem = new SimpleItem(mat, durability);
				} else {
					rItem = new SimpleItem(mat);
				}
				rItem.setAmount(amount);
				rItem.makeImmutable();
				ingredients.add(rItem);
				BCauldronRecipe.acceptedMaterials.add(mat);
				BCauldronRecipe.acceptedSimple.add(mat);
			} else {
				BreweryPlugin.getInstance().errorLog(recipeId + ": Unknown Material: " + ingredParts[0]);
				return null;
			}
		}
		return ingredients;
	}

	/**
	 * Load a list of strings from a ConfigurationSection and parse the quality
	 */
	@Nullable
	public static Map<Integer, String> loadQualityStringList(ConfigurationSection cfg, String path, StringParser.ParseType parseType) {
		List<String> load = BUtil.loadCfgStringList(cfg, path);
		if (load != null) {
			return loadQualityStringList(load, parseType);
		}
		return null;
	}

	public static Map<Integer, String> loadQualityStringList(List<String> stringList, StringParser.ParseType parseType) {
		Map<Integer, String> result = new HashMap<>();
		for (String line : stringList) {
			Tuple<Integer, String> parsed = StringParser.parseQuality(line, parseType);
			result.put(parsed.first(), parsed.second());
		}
		return result;
	}

	/**
	 * check every part of the recipe for validity.
	 */
	public boolean isValid() {
		if (ingredients == null || ingredients.isEmpty()) {
			BreweryPlugin.getInstance().errorLog("No ingredients could be loaded for Recipe: " + getRecipeName());
			return false;
		}
		if (cookingTime < 1) {
			BreweryPlugin.getInstance().errorLog("Invalid cooking time '" + cookingTime + "' in Recipe: " + getRecipeName());
			return false;
		}
		if (distillruns < 0) {
			BreweryPlugin.getInstance().errorLog("Invalid distillruns '" + distillruns + "' in Recipe: " + getRecipeName());
			return false;
		}
		if (distillTime < 0) {
			BreweryPlugin.getInstance().errorLog("Invalid distilltime '" + distillTime + "' in Recipe: " + getRecipeName());
			return false;
		}
		if (wood < 0 || wood > LegacyUtil.TOTAL_WOOD_TYPES) {
			BreweryPlugin.getInstance().errorLog("Invalid wood type '" + wood + "' in Recipe: " + getRecipeName());
			return false;
		}
		if (age < 0) {
			BreweryPlugin.getInstance().errorLog("Invalid age time '" + age + "' in Recipe: " + getRecipeName());
			return false;
		}
		if (difficulty < 0 || difficulty > 10) {
			BreweryPlugin.getInstance().errorLog("Invalid difficulty '" + difficulty + "' in Recipe: " + getRecipeName());
			return false;
		}
		return true;
	}

	/**
	 * allowed deviation to the recipes count of ingredients at the given difficulty
	 */
	public int allowedCountDiff(int count) {
		if (count < 8) {
			count = 8;
		}
		int allowedCountDiff = Math.round((float) ((11.0 - difficulty) * (count / 10.0)));

		if (allowedCountDiff == 0) {
			return 1;
		}
		return allowedCountDiff;
	}

	/**
	 * allowed deviation to the recipes cooking-time at the given difficulty
	 */
	public int allowedTimeDiff(int time) {
		if (time < 8) {
			time = 8;
		}
		int allowedTimeDiff = Math.round((float) ((11.0 - difficulty) * (time / 10.0)));

		if (allowedTimeDiff == 0) {
			return 1;
		}
		return allowedTimeDiff;
	}

	/**
	 * difference between given and recipe-wanted woodtype
	 */
	public float getWoodDiff(float wood) {
		return Math.abs(wood - this.wood);
	}

	public boolean isCookingOnly() {
		return age == 0 && distillruns == 0;
	}

	public boolean needsDistilling() {
		return distillruns != 0;
	}

	public boolean needsToAge() {
		return age != 0;
	}

	/**
	 * true if given list misses an ingredient
	 */
	public boolean isMissingIngredients(List<Ingredient> list) {
		if (list.size() < ingredients.size()) {
			return true;
		}
		for (RecipeItem rItem : ingredients) {
			boolean matches = false;
			for (Ingredient used : list) {
				if (rItem.matches(used)) {
					matches = true;
					break;
				}
			}
			if (!matches) {
				return true;
			}
		}
		return false;
	}

	public void applyDrinkFeatures(Player player, int quality) {
		List<String> playerCmdsForQuality = getPlayercmdsForQuality(quality);
		if (playerCmdsForQuality != null) {
			for (String cmd : playerCmdsForQuality) {
				scheduleCommand(player, cmd, player.getName(), quality, false);
			}
		}
		List<String> serverCmdsForQuality = getServercmdsForQuality(quality);
		if (serverCmdsForQuality != null) {
			for (String cmd : serverCmdsForQuality) {
				scheduleCommand(player, cmd, player.getName(), quality, true);
			}
		}
		if (drinkMsg != null) {
			player.sendMessage(BUtil.applyPlaceholders(drinkMsg, player.getName(), quality));
		}
		if (drinkTitle != null) {
			player.sendTitle("", BUtil.applyPlaceholders(drinkTitle, player.getName(), quality), 10, 90, 30);
		}
	}

	private void scheduleCommand(Player player, String cmd, String playerName, int quality, boolean isServerCommand) {
		if (cmd.startsWith("/")) cmd = cmd.substring(1);
		if (cmd.contains("/")) {
			String[] parts = cmd.split("/");
			String command = parts[0].trim(); // Needs to be effectively final for scheduling
			cmd = parts[0].trim();
			String delay = parts[1].trim();
			long delayTicks = parseDelayToTicks(delay);
			if (delayTicks > 0) {
				new BukkitRunnable() {
					@Override
					public void run() {
						executeCommand(player, command, playerName, quality, isServerCommand);
					}
				}.runTaskLater(BreweryPlugin.getInstance(), delayTicks);
				return;
			}
		}
		// Execute command immediately if no delay is specified
		executeCommand(player, cmd, playerName, quality, isServerCommand);
	}

	private long parseDelayToTicks(String delay) {
		try {
			if (delay.endsWith("s")) {
				int seconds = Integer.parseInt(delay.substring(0, delay.length() - 1));
				return seconds * 20L; // 20 ticks per second
			} else if (delay.endsWith("m")) {
				int minutes = Integer.parseInt(delay.substring(0, delay.length() - 1));
				return minutes * 1200L; // 1200 ticks per minute
			}
		} catch (NumberFormatException e) {
			// Invalid format: Default to 0
		}
		return 0; // Immediately execute command
	}

	private void executeCommand(Player player, String cmd, String playerName, int quality, boolean isServerCommand) {
		if (isServerCommand) {
			Bukkit.dispatchCommand(Bukkit.getConsoleSender(), BUtil.applyPlaceholders(cmd, playerName, quality));
		} else {
			player.performCommand(BUtil.applyPlaceholders(cmd, playerName, quality));
		}
	}

	/**
	 * Create a Potion from this Recipe with best values.
	 * Quality can be set, but will reset to 10 if unset immutable and put in a barrel
	 *
	 * @param quality The Quality of the Brew
	 * @return The Created Item
	 */
	public ItemStack create(int quality) {
		return createBrew(quality).createItem(this);
	}

	/**
	 * Create a Brew from this Recipe with best values.
	 * Quality can be set, but will reset to 10 if unset immutable and put in a barrel
	 *
	 * @param quality The Quality of the Brew
	 * @return The created Brew
	 */
	public Brew createBrew(int quality) {
		List<Ingredient> list = new ArrayList<>(ingredients.size());
		for (RecipeItem rItem : ingredients) {
			Ingredient ing = rItem.toIngredientGeneric();
			ing.setAmount(rItem.getAmount());
			list.add(ing);
		}

		BIngredients bIngredients = new BIngredients(list, cookingTime);

		return new Brew(bIngredients, quality, 0, distillruns, getAge(), wood, getRecipeName(), false, true, 0);
	}

	public void updateAcceptedLists() {
		for (RecipeItem ingredient : getIngredients()) {
			if (ingredient.hasMaterials()) {
				BCauldronRecipe.acceptedMaterials.addAll(ingredient.getMaterials());
			}
			if (ingredient instanceof SimpleItem) {
				BCauldronRecipe.acceptedSimple.add(((SimpleItem) ingredient).getMaterial());
			} else {
				// Add it as acceptedCustom
				if (!BCauldronRecipe.acceptedCustom.contains(ingredient)) {
					BCauldronRecipe.acceptedCustom.add(ingredient);
				}
			}
		}
	}


	// Getter

	/**
	 * how many of a specific ingredient in the recipe
	 */
	public int amountOf(Ingredient ing) {
		for (RecipeItem rItem : ingredients) {
			if (rItem.matches(ing)) {
				return rItem.getAmount();
			}
		}
		return 0;
	}

	/**
	 * how many of a specific ingredient in the recipe
	 */
	public int amountOf(ItemStack item) {
		for (RecipeItem rItem : ingredients) {
			if (rItem.matches(item)) {
				return rItem.getAmount();
			}
		}
		return 0;
	}

	/**
	 * Same as getName(5)
	 */
	public String getRecipeName() {
		return getName(5);
	}

	/**
	 * name that fits the quality
	 */
	public String getName(int quality) {
		if (name.length > 2) {
			if (quality <= 3) {
				return name[0];
			} else if (quality <= 7) {
				return name[1];
			} else {
				return name[2];
			}
		} else {
			return name[0];
		}
	}

	/**
	 * If one of the quality names equalIgnoreCase given name
	 */
	public boolean hasName(String name) {
		for (String test : this.name) {
			if (test.equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}


	public String getId() {
		return id;
	}

	public List<RecipeItem> getIngredients() {
		return ingredients;
	}

	public int getCookingTime() {
		return cookingTime;
	}

	public byte getDistillRuns() {
		return distillruns;
	}

	public int getDistillTime() {
		return distillTime;
	}

	@NotNull
	public PotionColor getColor() {
		return color;
	}

	/**
	 * get the woodtype
	 */
	public byte getWood() {
		return wood;
	}

	public float getAge() {
		return (float) age;
	}

	public int getDifficulty() {
		return difficulty;
	}

	public int getAlcohol() {
		return alcohol;
	}

	public boolean hasLore() {
		return lore != null && !lore.isEmpty();
	}

	@Nullable
	public Map<Integer, String> getLore() {
		return lore;
	}

	@Nullable
	public List<String> getLoreForQuality(int quality) {
		return getStringsForQuality(quality, lore);
	}

	@Nullable
	public List<String> getPlayercmdsForQuality(int quality) {
		return getStringsForQuality(quality, playercmds);
	}

	@Nullable
	public List<String> getServercmdsForQuality(int quality) {
		return getStringsForQuality(quality, servercmds);
	}

	/**
	 * Get a quality filtered list of supported attributes
	 */
	@Nullable
	public List<String> getStringsForQuality(int quality, Map<Integer, String> source) {
		if (source == null) return null;
		int plus;
		if (quality <= 3) {
			plus = 1;
		} else if (quality <= 7) {
			plus = 2;
		} else {
			plus = 3;
		}
		List<String> list = new ArrayList<>(source.size());
		for (Map.Entry<Integer, String> line : source.entrySet()) {
			if (line.getKey() == 0 || line.getKey() == plus) {
				list.add(line.getValue());
			}
		}
		return list;
	}

	/**
	 * Get the Custom Model Data array for bad, normal, good quality
	 */
	public int[] getCmData() {
		return cmData;
	}

	@Nullable
	public Map<Integer, String> getPlayercmds() {
		return playercmds;
	}

	@Nullable
	public Map<Integer, String> getServercmds() {
		return servercmds;
	}

	public String getDrinkMsg() {
		return drinkMsg;
	}

	public String getDrinkTitle() {
		return drinkTitle;
	}

	public boolean hasGlint() {
		return glint;
	}

	public List<BEffect> getEffects() {
		return effects;
	}

	public boolean isSaveInData() {
		return saveInData;
	}

	// Setters


	public void setName(String[] name) {
		this.name = name;
	}

	public void setCmData(int[] cmData) {
		this.cmData = cmData;
	}

	public void setId(String id) {
		this.id = id;
	}

	public void setDrinkTitle(String drinkTitle) {
		this.drinkTitle = drinkTitle;
	}

	public void setPlayercmds(@Nullable Map<Integer, String> playercmds) {
		this.playercmds = playercmds;
	}

	public void setServercmds(@Nullable Map<Integer, String> servercmds) {
		this.servercmds = servercmds;
	}

	public void setGlint(boolean glint) {
		this.glint = glint;
	}


	public void setDrinkMsg(String drinkMsg) {
		this.drinkMsg = drinkMsg;
	}

	/**
	 * When Changing ingredients, Accepted Lists have to be updated in BCauldronRecipe
	 */
	public void setIngredients(List<RecipeItem> ingredients) {
		this.ingredients = ingredients;
	}

	public void setCookingTime(int cookingTime) {
		this.cookingTime = cookingTime;
	}

	public void setDistillRuns(byte distillRuns) {
		this.distillruns = distillRuns;
	}

	public void setDistillTime(int distillTime) {
		this.distillTime = distillTime;
	}

	public void setWood(byte wood) {
		this.wood = wood;
	}

	public void setAge(int age) {
		this.age = age;
	}

	public void setColor(@NotNull PotionColor color) {
		this.color = color;
	}

	public void setDifficulty(int difficulty) {
		this.difficulty = difficulty;
	}

	public void setAlcohol(int alcohol) {
		this.alcohol = alcohol;
	}

	public void setLore(Map<Integer, String> lore) {
		this.lore = lore;
	}

	public void setEffects(List<BEffect> effects) {
		this.effects = effects;
	}

	public void setSaveInData(boolean saveInData) {
		throw new UnsupportedOperationException();
	}


	@Override
	public String toString() {
		return "BRecipe{" +
				"name=" + Arrays.toString(name) +
				", ingredients=" + ingredients +
				", difficulty=" + difficulty +
				", cookingTime=" + cookingTime +
				", distillruns=" + distillruns +
				", distillTime=" + distillTime +
				", wood=" + wood +
				", age=" + age +
				", color=" + color +
				", alcohol=" + alcohol +
				", lore=" + lore +
				", cmData=" + Arrays.toString(cmData) +
				", effects=" + effects +
				", playercmds=" + playercmds +
				", servercmds=" + servercmds +
				", drinkMsg='" + drinkMsg + '\'' +
				", drinkTitle='" + drinkTitle + '\'' +
				", glint=" + glint +
				'}';
	}

	/**
	 * Gets a Modifiable Sublist of the Recipes that are loaded by config.
	 * <p>Changes are directly reflected by the main list of all recipes
	 * <br>Changes to the main List of all recipes will make the reference to this sublist invalid
	 *
	 * <p>After adding or removing elements, BRecipe.numConfigRecipes MUST be updated!
	 */
	public static List<BRecipe> getConfigRecipes() {
		return recipes.subList(0, numConfigRecipes);
	}

	/**
	 * Gets a Modifiable Sublist of the Recipes that are added by plugins.
	 * <p>Changes are directly reflected by the main list of all recipes
	 * <br>Changes to the main List of all recipes will make the reference to this sublist invalid
	 */
	public static List<BRecipe> getAddedRecipes() {
		return recipes.subList(numConfigRecipes, recipes.size());
	}

	/**
	 * Gets the main List of all recipes.
	 */
	public static List<BRecipe> getAllRecipes() {
		return recipes;
	}



	public String[] getName() {
		return name;
	}

	public boolean isGlint() {
		return glint;
	}



	/**
	 * Get the BRecipe that has the given name as one of its quality names.
	 */
	@Nullable
	public static BRecipe getMatching(String name) {
		BRecipe mainNameRecipe = get(name);
		if (mainNameRecipe != null) {
			return mainNameRecipe;
		}
		for (BRecipe recipe : recipes) {
			if (recipe.getName(1).equalsIgnoreCase(name)) {
				return recipe;
			} else if (recipe.getName(10).equalsIgnoreCase(name)) {
				return recipe;
			}
		}
		for (BRecipe recipe : recipes) {
			if (name.equalsIgnoreCase(recipe.getId())) {
				return recipe;
			}
		}
		return null;
	}

	@Nullable
	public static BRecipe getById(String id) {
		for (BRecipe recipe : recipes) {
			if (id.equals(recipe.getId())) {
				return recipe;
			}
		}
		return null;
	}


	/**
	 * Get the BRecipe that has that name as its name
	 */
	@Nullable
	public static BRecipe get(String name) {
		for (BRecipe recipe : recipes) {
			if (recipe.getRecipeName().equalsIgnoreCase(name)) {
				return recipe;
			}
		}
		return null;
	}

    @Override
    public BRecipe clone() {
        try {
            BRecipe clone = (BRecipe) super.clone();
			clone.name = this.name.clone();
			clone.ingredients = new ArrayList<>(this.ingredients.size());
			for (RecipeItem item : this.ingredients) {
				clone.ingredients.add(item.getMutableCopy());
			}
			clone.lore = (this.lore != null) ? new HashMap<>(this.lore) : null;
			clone.playercmds = (this.playercmds != null) ? new HashMap<>(this.playercmds) : null;
			clone.servercmds = (this.servercmds != null) ? new HashMap<>(this.servercmds) : null;
			clone.effects = new ArrayList<>(this.effects.size());
			for (BEffect effect : this.effects) {
				clone.effects.add(effect.clone());
			}
			clone.cmData = (this.cmData != null) ? this.cmData.clone() : null;
			clone.drinkMsg = this.drinkMsg;
			clone.drinkTitle = this.drinkTitle;
			clone.glint = this.glint;
			clone.saveInData = this.saveInData;
			clone.id = this.id;
			clone.difficulty = this.difficulty;
			clone.cookingTime = this.cookingTime;
			clone.distillruns = this.distillruns;
			clone.distillTime = this.distillTime;
			clone.wood = this.wood;
			clone.age = this.age;
			clone.color = this.color;
			clone.alcohol = this.alcohol;
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

	/*public static void saveAddedRecipes(ConfigurationSection cfg) {
		int i = 0;
		for (BRecipe recipe : getAddedRecipes()) {
			if (recipe.isSaveInData()) {
				cfg.set(i + ".name", recipe.name);
			}
		}
	}*/


	/**
	 * Builder to easily create Recipes
	 */
	public static class Builder {
		private BRecipe recipe;

		public Builder(String name) {
			recipe = new BRecipe(name, PotionColor.WATER);
		}

		public Builder(String... names) {
			recipe = new BRecipe(names, PotionColor.WATER);
		}


		public Builder addIngredient(RecipeItem... item) {
			Collections.addAll(recipe.ingredients, item);
			return this;
		}

		public Builder addIngredient(ItemStack... item) {
			for (ItemStack i : item) {
				CustomItem customItem = new CustomItem(i);
				customItem.setAmount(i.getAmount());
				recipe.ingredients.add(customItem);
			}
			return this;
		}

		public Builder difficulty(int difficulty) {
			recipe.difficulty = difficulty;
			return this;
		}

		public Builder color(String colorString) {
			recipe.color = PotionColor.fromString(colorString);
			return this;
		}

		public Builder color(PotionColor color) {
			recipe.color = color;
			return this;
		}

		public Builder color(Color color) {
			recipe.color = PotionColor.fromColor(color);
			return this;
		}

		public Builder cook(int cookTime) {
			recipe.cookingTime = cookTime;
			return this;
		}

		public Builder distill(byte distillRuns, int distillTime) {
			recipe.distillruns = distillRuns;
			recipe.distillTime = distillTime;
			return this;
		}

		public Builder age(int age, byte wood) {
			recipe.age = age;
			recipe.wood = wood;
			return this;
		}

		public Builder alcohol(int alcohol) {
			recipe.alcohol = alcohol;
			return this;
		}

		public Builder addLore(String line) {
			return addLore(0, line);
		}

		/**
		 * Add a Line of Lore
		 *
		 * @param quality 0 for any quality, 1: bad, 2: normal, 3: good
		 * @param line    The Line for custom lore to add
		 * @return this
		 */
		public Builder addLore(int quality, String line) {
			if (quality < 0 || quality > 3) {
				throw new IllegalArgumentException("Lore Quality must be 0 - 3");
			}
			if (recipe.lore == null) {
				recipe.lore = new HashMap<>();
			}
			recipe.lore.put(quality, line);
			return this;
		}

		/**
		 * Add Commands that are executed by the player on drinking
		 */
		public Builder addPlayerCmds(String... cmds) {
			Map<Integer, String> playercmds = new HashMap<>(cmds.length);

			for (String cmd : cmds) {
				Tuple<Integer, String> parsedQuality = StringParser.parseQuality(cmd, StringParser.ParseType.CMD);
				playercmds.put(parsedQuality.first(), parsedQuality.second());
			}
			if (recipe.playercmds == null) {
				recipe.playercmds = playercmds;
			} else {
				recipe.playercmds.putAll(playercmds);
			}
			return this;
		}

		/**
		 * Add Commands that are executed by the server on drinking
		 */
		public Builder addServerCmds(String... cmds) {
			Map<Integer, String> servercmds = new HashMap<>(cmds.length);

			for (String cmd : cmds) {
				Tuple<Integer, String> parsedQuality = StringParser.parseQuality(cmd, StringParser.ParseType.CMD);
				servercmds.put(parsedQuality.first(), parsedQuality.second());
			}
			if (recipe.servercmds == null) {
				recipe.servercmds = servercmds;
			} else {
				recipe.servercmds.putAll(servercmds);
			}
			return this;
		}

		/**
		 * Add Message that is sent to the player in chat when he drinks the brew
		 */
		public Builder drinkMsg(String msg) {
			recipe.drinkMsg = msg;
			return this;
		}

		/**
		 * Add Message that is sent to the player as a small title when he drinks the brew
		 */
		public Builder drinkTitle(String title) {
			recipe.drinkTitle = title;
			return this;
		}

		/**
		 * Add a Glint to the Potion
		 */
		public Builder glint(boolean glint) {
			recipe.glint = glint;
			return this;
		}

		/**
		 * Set the Optional ID of this recipe
		 */
		public Builder setID(String id) {
			recipe.id = id;
			return this;
		}

		/**
		 * Add Custom Model Data for each Quality
		 */
		public Builder addCustomModelData(int bad, int normal, int good) {
			recipe.cmData = new int[] {bad, normal, good};
			return this;
		}

		public Builder addEffects(BEffect... effects) {
			Collections.addAll(recipe.effects, effects);
			return this;
		}

		public BRecipe get() {
			if (recipe.name == null) {
				throw new IllegalArgumentException("Recipe name is null");
			}
			if (recipe.name.length != 1 && recipe.name.length != 3) {
				throw new IllegalArgumentException("Recipe name neither 1 nor 3");
			}
			if (recipe.color == null) {
				throw new IllegalArgumentException("Recipe has no color");
			}
			if (recipe.ingredients == null || recipe.ingredients.isEmpty()) {
				throw new IllegalArgumentException("Recipe has no ingredients");
			}
			if (!recipe.isValid()) {
				throw new IllegalArgumentException("Recipe has not valid");
			}
			for (RecipeItem ingredient : recipe.ingredients) {
				ingredient.makeImmutable();
			}
			return recipe;
		}
	}
}
