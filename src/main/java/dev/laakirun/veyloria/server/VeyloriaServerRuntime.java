package dev.laakirun.veyloria.server;

import dev.laakirun.veyloria.common.config.RatesConfig;
import dev.laakirun.veyloria.common.config.ServerConfig;
import dev.laakirun.veyloria.server.auth.AuthService;
import dev.laakirun.veyloria.server.auth.PasswordHasher;
import dev.laakirun.veyloria.server.auth.SessionManager;
import dev.laakirun.veyloria.server.content.ContentService;
import dev.laakirun.veyloria.server.db.DatabaseManager;
import dev.laakirun.veyloria.server.db.MigrationService;
import dev.laakirun.veyloria.server.db.SeedImporter;
import dev.laakirun.veyloria.server.game.AuthLockService;
import dev.laakirun.veyloria.server.game.CommonMobAiService;
import dev.laakirun.veyloria.server.game.GearDropService;
import dev.laakirun.veyloria.server.game.ItemFactory;
import dev.laakirun.veyloria.server.game.LootService;
import dev.laakirun.veyloria.server.game.MobSpawnService;
import dev.laakirun.veyloria.server.game.PartyService;
import dev.laakirun.veyloria.server.game.PlayerLoadoutService;
import dev.laakirun.veyloria.server.game.PlayerStatService;
import dev.laakirun.veyloria.server.game.TestWorldLayoutService;
import dev.laakirun.veyloria.server.profile.CharacterService;
import dev.laakirun.veyloria.server.profile.LevelService;
import dev.laakirun.veyloria.server.structure.StructureService;
import dev.laakirun.veyloria.common.targeting.TargetingProfile;
import dev.laakirun.veyloria.common.targeting.TargetingService;

public final class VeyloriaServerRuntime {
    private static final VeyloriaServerRuntime INSTANCE = new VeyloriaServerRuntime();

    private ServerConfig serverConfig;
    private RatesConfig baseRatesConfig;
    private volatile RatesConfig ratesConfig;
    private DatabaseManager databaseManager;
    private ContentService contentService;
    private AuthService authService;
    private CharacterService characterService;
    private LevelService levelService;
    private AuthLockService authLockService;
    private PlayerStatService playerStatService;
    private ItemFactory itemFactory;
    private LootService lootService;
    private MobSpawnService mobSpawnService;
    private CommonMobAiService commonMobAiService;
    private TestWorldLayoutService testWorldLayoutService;
    private PartyService partyService;
    private GearDropService gearDropService;
    private PlayerLoadoutService playerLoadoutService;
    private StructureService structureService;
    private TargetingService targetingService;
    private TargetingProfile targetingProfile;

    private VeyloriaServerRuntime() {
    }

    public static VeyloriaServerRuntime instance() {
        return INSTANCE;
    }

    public void initialize(ServerConfig serverConfig, RatesConfig ratesConfig) {
        this.serverConfig = serverConfig;
        this.baseRatesConfig = ratesConfig;
        this.ratesConfig = ratesConfig;
        this.databaseManager = new DatabaseManager(serverConfig);
        this.databaseManager.initialize();
        new MigrationService(databaseManager).migrate();
        new SeedImporter(databaseManager).importSeeds();
        this.contentService = new ContentService(databaseManager);
        this.contentService.reload();
        this.authService = new AuthService(databaseManager, new PasswordHasher(), new SessionManager());
        this.characterService = new CharacterService(databaseManager);
        this.levelService = new LevelService();
        this.authLockService = new AuthLockService();
        this.playerStatService = new PlayerStatService();
        this.itemFactory = new ItemFactory();
        this.lootService = new LootService(contentService);
        this.mobSpawnService = new MobSpawnService();
        this.commonMobAiService = new CommonMobAiService();
        this.testWorldLayoutService = new TestWorldLayoutService();
        this.partyService = new PartyService();
        this.gearDropService = new GearDropService();
        this.playerLoadoutService = new PlayerLoadoutService();
        this.structureService = new StructureService(databaseManager);
        this.targetingService = new TargetingService();
        this.targetingProfile = TargetingProfile.defaults();
    }

    public ServerConfig serverConfig() {
        return serverConfig;
    }

    public RatesConfig ratesConfig() {
        return ratesConfig;
    }

    public RatesConfig baseRatesConfig() {
        return baseRatesConfig;
    }

    public void overrideRates(RatesConfig ratesConfig) {
        this.ratesConfig = ratesConfig;
    }

    public void resetRatesOverrides() {
        this.ratesConfig = baseRatesConfig;
    }

    public DatabaseManager databaseManager() {
        return databaseManager;
    }

    public ContentService contentService() {
        return contentService;
    }

    public AuthService authService() {
        return authService;
    }

    public CharacterService characterService() {
        return characterService;
    }

    public LevelService levelService() {
        return levelService;
    }

    public AuthLockService authLockService() {
        return authLockService;
    }

    public PlayerStatService playerStatService() {
        return playerStatService;
    }

    public ItemFactory itemFactory() {
        return itemFactory;
    }

    public LootService lootService() {
        return lootService;
    }

    public MobSpawnService mobSpawnService() {
        return mobSpawnService;
    }

    public CommonMobAiService commonMobAiService() {
        return commonMobAiService;
    }

    public TestWorldLayoutService testWorldLayoutService() {
        return testWorldLayoutService;
    }

    public PartyService partyService() {
        return partyService;
    }

    public GearDropService gearDropService() {
        return gearDropService;
    }

    public PlayerLoadoutService playerLoadoutService() {
        return playerLoadoutService;
    }

    public StructureService structureService() {
        return structureService;
    }

    public TargetingService targetingService() {
        return targetingService;
    }

    public TargetingProfile targetingProfile() {
        return targetingProfile;
    }
}
