package com.knoban.ultimates;

import com.knoban.atlas.battlepass.BattlePassManager;
import com.knoban.atlas.claims.GenericEstateListener;
import com.knoban.atlas.claims.LandManager;
import com.knoban.atlas.commandsII.ACAPI;
import com.knoban.atlas.data.firebase.AtlasFirebase;
import com.knoban.atlas.data.local.DataHandler.YML;
import com.knoban.atlas.missions.MissionManager;
import com.knoban.atlas.missions.Missions;
import com.knoban.atlas.missions.bossbar.BossBarAnimationHandler;
import com.knoban.atlas.rewards.Rewards;
import com.knoban.ultimates.aspects.*;
import com.knoban.ultimates.aspects.warmup.ActionWarmupManager;
import com.knoban.ultimates.cardholder.CardHolder;
import com.knoban.ultimates.cardholder.Holder;
import com.knoban.ultimates.cardholder.OfflineCardHolder;
import com.knoban.ultimates.cards.Card;
import com.knoban.ultimates.cards.Cards;
import com.knoban.ultimates.cards.GeneralCardListener;
import com.knoban.ultimates.cards.impl.*;
import com.knoban.ultimates.claims.UltimatesEstateListener;
import com.knoban.ultimates.commands.*;
import com.knoban.ultimates.commands.parsables.PrimalSourceParsable;
import com.knoban.ultimates.player.LocalPDStoreManager;
import com.knoban.ultimates.primal.PrimalSource;
import com.knoban.ultimates.primal.Tier;
import com.knoban.ultimates.rewards.*;
import com.knoban.ultimates.tutorial.HelperSuggestionsListener;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Ultimates! Get cards, unlock special abilities, collect them all. Each card gives you a unique effect ingame.
 * Join a primal, claim some land, start some conflict. Use your cards to get an edge on other players.
 * <br><br>
 * This project was written for GodComplex LLC. This copy of code is a branch of Ultimates containing all of
 * Alden's solo work before the game was picked up and maintained by the rest of the team. It serves the purpose
 * of being an NDA'd Java profile piece considering the scale of the game. (2019-2021)
 *
 * @author Alden Bansemer (kNoAPP)
 */
public class Ultimates extends JavaPlugin {

	private YML config;
	private File claimsFolder;

	private AtlasFirebase firebase;
	private LocalPDStoreManager lpdsm;
	private AlohaListener aloha;
	private GeneralCardListener gcl;
	private LandManager landManager;
	private CombatStateManager combatManager;
	private MoveCallbackManager moveCallbackManager;
	private ActionWarmupManager actionWarmupManager;
	private BossBarAnimationHandler bossBarAnimationHandler;
	private BattlePassManager battlepassManager;
	private MissionManager missionManager;

	private static Ultimates plugin;
	
	private boolean failed = false;
	
	@Override
	public void onEnable() {
		long tStart = System.currentTimeMillis();
		plugin = this;
		importData();
		importAspects();
		addRecipies();
		reportInfo();
		long tEnd = System.currentTimeMillis();
		getLogger().info("Successfully Enabled! (" + (tEnd - tStart) + " ms)");
		
		if(failed) {
			getLogger().warning("Unfortunately, Ultimates ran into errors on startup. It will disable itself now.");
			getLogger().info("If you're using this plugin for the first time, please make sure that you have: ");
			getLogger().info("1. Added Atlas as a plugin (and properly configured it).");
			getLogger().info("2. Created a Firebase project at: https://firebase.google.com/");
			getLogger().info("3. Imported the admin key to the Ultimates plugin folder.");
			getLogger().info("  - See (https://console.firebase.google.com/u/0/project/_/settings/serviceaccounts/adminsdk)");
			getLogger().info("4. Told Ultimates/config.yml about your Firebase URL and key location.");
		}
	}
	
	@Override
	public void onDisable() {
		long tStart = System.currentTimeMillis();
		exportAspects();
		exportData();
		long tEnd = System.currentTimeMillis();
		getLogger().info("Successfully Disabled! (" + (tEnd - tStart) + " ms)");
	}

	
	private void addRecipies() {
		getServer().addRecipe(Items.getRespawnRecipe());
	}
	
	private void importData() {
		if(failed)
			return;
		getLogger().info("Importing data files...");

		config = new YML(this, "/config.yml");
		FileConfiguration fc = config.getCachedYML();

		Holder.setCardsDrawOnLoadSaveOnUnload(fc.getBoolean("Cards.Draw-On-Load-Save-On-Unload", true));
		CardHolder.setPrimalsUseScoreboard(fc.getBoolean("Primals.Use-Scoreboard", true));

		try {
			firebase = new AtlasFirebase(fc.getString("Firebase.DatabaseURL"), new File(getDataFolder(),
					fc.getString("Firebase.Key")));
		} catch(IOException e) {
			getLogger().warning("Could not authenticate with Firebase: " + e.getMessage());
			failed = true;
			return;
		}

		claimsFolder = new File(getDataFolder(), "claims");
		claimsFolder.mkdirs();

		// Land Manager Initialization
		landManager = LandManager.createLandManager(this, claimsFolder);
		new GenericEstateListener(this, landManager);
		new UltimatesEstateListener(this, landManager);
	}
	
	private void importAspects() {
		if(failed)
			return;
		
		getLogger().info("Importing aspects...");

		FileConfiguration fc = config.getCachedYML();

		ACAPI.getApi().addParser(PrimalSource.class, new PrimalSourceParsable());

		lpdsm = new LocalPDStoreManager(this);
		combatManager = new CombatStateManager(this, TimeUnit.SECONDS.toMillis(8));
		moveCallbackManager = new MoveCallbackManager(this);
		actionWarmupManager = new ActionWarmupManager(this);
		bossBarAnimationHandler = new BossBarAnimationHandler(this);
		registerMissionsToAtlas();
		registerRewardsToAtlas();
		registerCardsToUltimates();
		battlepassManager = new BattlePassManager(this, firebase, "/battlepass");
		missionManager = new MissionManager(this, firebase, "/missions");
		aloha = new AlohaListener(this);
		gcl = new GeneralCardListener(this);

		new GeneralListener(this);
		if(fc.getBoolean("Tutorial.Helpful-Suggestions", true))
			new HelperSuggestionsListener(this);

		if(fc.getBoolean("Command-Toggle.BattlePass", true))
			new BattlePassCommandHandle(this);
		if(fc.getBoolean("Command-Toggle.Card", true))
			new CardCommandHandle(this);
		if(fc.getBoolean("Command-Toggle.CardPack", true))
			new CardPackCommandHandle(this);
		if(fc.getBoolean("Command-Toggle.CardSlot", true))
			new CardSlotCommandHandle(this);
		if(fc.getBoolean("Command-Toggle.Estate", true))
			new EstateCommandHandle(this);
		if(fc.getBoolean("Command-Toggle.Flash", true))
			new FlashCommandHandle(this);
		if(fc.getBoolean("Command-Toggle.Level", true))
			new LevelCommandHandle(this);
		if(fc.getBoolean("Command-Toggle.Recall", true))
			new RecallCommandHandle(this);
		if(fc.getBoolean("Command-Toggle.Soundgen", true))
			new SoundgenCommandHandle(this);
		if(fc.getBoolean("Command-Toggle.Ultimates", true))
			new UltimatesCommandHandle(this);
		if(fc.getBoolean("Command-Toggle.Wisdom", true))
			new WisdomCommandHandle(this);

		// Disable cards
		for(String cardName : fc.getStringList("Cards.DisableThese")) {
			Card c = Cards.getInstance().getCardInstanceByName(cardName);
			if(c != null) {
				c.setEnabled(false);
			}
		}

		for(Player pl : Bukkit.getOnlinePlayers()) {
			CardHolder.getCardHolder(pl).login();
			aloha.join(pl);
		}
	}

	private void registerCardsToUltimates() {
		Cards cards = Cards.getInstance();
		Arrays.asList(OOCRegenerationCard.class, CultivatorCard.class, WormCard.class, RubberSkinCard.class,
				ForceLevitationCard.class, StrangeBowCard.class, VeganCard.class, RubberProjectileCard.class,
				ZeroGravityProjectileCard.class, DeflectionCard.class, MagmaWalkerCard.class,
				SplashPotionOfGetHisAssCard.class, ScavengerCard.class, LumberjackCard.class, LuckCard.class,
				SoulCard.class, FallCard.class, TwinsCard.class, EnlightenedCard.class, PokeCard.class, TeemoCard.class,
				FlashbangCard.class, DruidCard.class, XRayCard.class, PortalCard.class, JuggernautCard.class,
				TankCard.class, HotHandsCard.class, DryadsGiftCard.class, RunnersDietCard.class,
				PantherCard.class, SpeedCard.class, SteadyHandsCard.class, AnchorCard.class, UnyieldingMightCard.class,
				FalconCard.class, ParleyCard.class, SchoolingCard.class, ShadowsUpriseCard.class, RealityPhaseCard.class
		).forEach(cards::addCard);
	}

	private void registerMissionsToAtlas() {
		Missions missions = Missions.getInstance();
		// TODO Register missions when created.
	}

	private void registerRewardsToAtlas() {
		Rewards rewards = Rewards.getInstance();
		Arrays.asList(CardReward.class, CardSlotReward.class, EstateClaimReward.class, ExperienceReward.class,
				WisdomReward.class
		).forEach(rewards::addReward);
	}

	public void reportInfo() {
		if(failed)
			return;

		getLogger().info("Tier normalization report:");
		for(Tier tier : Tier.values())
			getLogger().info(tier.getDisplay() + "§7: " + tier.getChance());

		getLogger().info("");
		getLogger().info("Card breakdowns:");
		PrimalSource current = PrimalSource.NONE;
		int amt = 0;
		for(Card card : Cards.getInstance().getCardInstancesByPrimal()) {
			if(card.getInfo().source() != current) {
				getLogger().info(current.getDisplay() + "§7: " + amt);
				current = card.getInfo().source();
				amt = 0;
			}
			++amt;
		}
		getLogger().info(current.getDisplay() + "§7: " + amt);
	}
	
	public void exportData() {
		if(failed)
			return;
		getPlugin().getLogger().info("Exporting data files...");

		landManager.saveLandManager(claimsFolder);
	}

	public static final String SHUTDOWN_MESSAGE = "Server Shutdown (Kicking Player...)";
	private void exportAspects() {
		if(failed)
			return;
		
		getLogger().info("Exporting aspects...");
		
		combatManager.shutdown();
		moveCallbackManager.shutdown();
		actionWarmupManager.shutdown();

		// Disconnect all players means we need to save data!
		for(Player pl : Bukkit.getOnlinePlayers())
			aloha.quit(pl, true);

		OfflineCardHolder.safeShutdown(this);
		battlepassManager.safeShutdown();
		missionManager.safeShutdown();

		lpdsm.clearCacheAndSave();
	}

	public AtlasFirebase getFirebase() {
		return firebase;
	}

	public LocalPDStoreManager getPlayerDataStore() {
		return lpdsm;
	}

	public LandManager getLandManager() {
		return landManager;
	}

	public BossBarAnimationHandler getBossBarAnimationHandler() {
		return bossBarAnimationHandler;
	}

	public BattlePassManager getBattlepassManager() {
		return battlepassManager;
	}

	public MissionManager getMissionManager() {
		return missionManager;
	}

	public CombatStateManager getCombatManager() {
		return combatManager;
	}

	public MoveCallbackManager getMoveCallbackManager() {
		return moveCallbackManager;
	}

	public ActionWarmupManager getActionWarmupManager() {
		return actionWarmupManager;
	}

	public YML getConfigFile() {
		return config;
	}

	public static Ultimates getPlugin() {
		return plugin;
	}
}
