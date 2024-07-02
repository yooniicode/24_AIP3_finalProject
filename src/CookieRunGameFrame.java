import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import loot.GameFrame;
import loot.GameFrameSettings;
import loot.graphics.DrawableObject;

@SuppressWarnings("serial")
public class CookieRunGameFrame extends GameFrame {

    static final int lifebar_width = 350; // HP 게이지의 가로 길이
    static final int player_maxHP = 300;  // 플레이어의 최대 HP
    static final int normalSpeed = -5;
    static final int cheatSpeed = (int) (normalSpeed * 1.25);
    static final int jumpSlowDown = -3;   // 점프 시 x 방향의 속도 감소
    static final long gameDuration = 15000; // 게임 클리어 시간 (15초)
    static final long itemSpawnInterval = 8000; // 아이템 생성 간격 (8초)

    enum GameState {
        Started,    // 게임이 아직 시작되지 않은 상태
        Ready,      // 스페이스 바를 누른 상태, 이제 떼면 게임이 시작됨
        Running,    // 게임이 시작된 상태
        Finished,   // 승자가 결정된 상태
        Stopped,    // 일시 중지 상태
        Info,       // 정보 창 상태
        Cleared     // 게임 클리어 상태
    }

    enum PlayerState {
        sliding,    // 슬라이드 중
        jumping,    // 점프 중
        running     // 달리기 중
    }

    class Player extends DrawableObject {
        public PlayerState state;
        public int HP;
        public LifeBar lifeBar;

        public int yVelocity;
        public int xVelocity;
        public boolean jumping;
        public boolean sliding;
        public boolean invincible; // 무적 모드 활성화 여부
        public long invincibleTime;

        public Player(boolean isPlayer1) {
            state = PlayerState.running;
            HP = player_maxHP;
            lifeBar = new LifeBar();
            x = 50;
            y = 300;
            width = (int) (90 * 1.5);
            height = (int) (112 * 1.5);
            xVelocity = normalSpeed;

            image = images.GetImage("player");
        }

        public void jump() {
            if (!jumping && !sliding) {
                yVelocity = -20;
                xVelocity = jumpSlowDown; // 점프 시 x 방향 속도 감소
                jumping = true;
            }
        }

        public void slide() {
            if (!jumping && !sliding) {
                y = 350; // 슬라이드 시 낮아짐
                sliding = true;
                state = PlayerState.sliding;
            }
        }

        public void stopSliding() {
            if (sliding) {
                y = 300; // 원래 위치로 돌아감
                sliding = false;
                state = PlayerState.running;
            }
        }

        public void update() {
            if (jumping) {
                y += yVelocity;
                yVelocity += 1;
                if (y >= 300) {
                    y = 300;
                    yVelocity = 0;
                    xVelocity = normalSpeed; // 점프가 끝나면 x 방향 속도 복구
                    jumping = false;
                }
            }
            // 무적 모드 남은 시간 감소
            if (invincible) {
                invincibleTime -= 1;
                if (invincibleTime <= 0) {
                    invincible = false;
                }
            }
        }

        @Override
        public void draw(java.awt.Graphics g) {
            if (image != null) {
                g.drawImage(image, x, y, width, height, null);
            }
        }
    }

    class Obstacle extends DrawableObject {
        private int speed;

        public Obstacle(int x, int y) {
            this.x = x;
            this.y = y;
            this.width = (int) (70 * 0.7);
            this.height = (int) (77 * 0.7);
            this.speed = normalSpeed;
            this.image = images.GetImage("obstacle");
        }

        public void update() {
            x += speed;
            if (x < -70) {
                x = 800;
                y = new Random().nextInt(150) + 250; // 장애물의 y 좌표를 랜덤하게 설정 (250 ~ 400)
            }
        }

        public void setCheatMode(boolean isCheatMode) {
            this.speed = isCheatMode ? cheatSpeed : normalSpeed;
        }

        @Override
        public void draw(java.awt.Graphics g) {
            if (image != null) {
                g.drawImage(image, x, y, width, height, null);
            }
        }
    }

    class LifeBar extends DrawableObject {
        public LifeBar() {
            x = 10;
            y = 10;
            width = lifebar_width;
            height = 20;

            image = images.GetImage("lifebar_green");
        }

        @Override
        public void draw(java.awt.Graphics g) {
            if (image != null) {
                g.drawImage(image, x, y, width, height, null);
            }
        }
    }

    class Item extends DrawableObject {
        private int speed;
        public boolean active; // 아이템 활성화 여부

        public Item(int x, int y) {
            this.x = x;
            this.y = y;
            this.width = 30;
            this.height = 30;
            this.speed = normalSpeed;
            this.image = images.GetImage("item");
            this.active = true; // 아이템이 활성화된 상태로 초기화
        }

        public void update() {
            if (active) {
                x += speed;
                if (x < -30) {
                    active = false; // 화면 밖으로 나가면 비활성화
                }
            }
        }

        public void setCheatMode(boolean isCheatMode) {
            this.speed = isCheatMode ? cheatSpeed : normalSpeed;
        }

        @Override
        public void draw(java.awt.Graphics g) {
            if (image != null) {
                g.drawImage(image, x, y, width, height, null);
            }
        }
    }

    /* -------------------------------------------
     * 
     * 필드 선언 부분
     * 
     */

    Player player;
    List<Obstacle> obstacles;
    List<Item> items;
    GameState state = GameState.Started; // 정상적인 경우 Started -> Ready -> Running -> Finished -> Ready -> ...의 순서로 전환됨
    int score;
    boolean gameRunning;
    boolean cheatModeAvailable;
    boolean cheatModeActive;
    long startTime; // 게임 시작 시간
    long lastItemSpawnTime; // 마지막 아이템 생성 시간

    /* -------------------------------------------
     * 
     * 메서드 정의 부분
     * 
     */

    public CookieRunGameFrame(GameFrameSettings settings) {
        super(settings);

        inputs.BindKey(KeyEvent.VK_SPACE, 0);
        inputs.BindKey(KeyEvent.VK_UP, 1);
        inputs.BindKey(KeyEvent.VK_DOWN, 2);
        inputs.BindKey(KeyEvent.VK_I, 3);
        inputs.BindKey(KeyEvent.VK_F1, 4);
        inputs.BindKey(KeyEvent.VK_F2, 5);

        images.LoadImage("Images/player.png", "player");
        images.LoadImage("Images/obstacle.png", "obstacle");
        images.LoadImage("Images/ball.png", "item");
        images.LoadImage("Images/lifebar_green.png", "lifebar_green");
        images.LoadImage("Images/lifebar_red.png", "lifebar_red");
    }

    @Override
    public boolean Initialize() {
        player = new Player(true);
        obstacles = new ArrayList<>();
        items = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            obstacles.add(new Obstacle(800 + i * 200, new Random().nextInt(150) + 250));
            items.add(new Item(800 + i * 400, new Random().nextInt(150) + 250)); // 아이템도 장애물과 함께 생성
        }

        score = 0;
        gameRunning = true;
        cheatModeAvailable = false;
        cheatModeActive = false;
        startTime = System.currentTimeMillis(); // 게임 시작 시간 초기화
        lastItemSpawnTime = startTime; // 마지막 아이템 생성 시간 초기화
        
        return true;
    }

    @Override
    public boolean Update(long timeStamp) {
        inputs.AcceptInputs(); // Ensure inputs are accepted

        switch (state) {
            case Ready:
                if (inputs.buttons[0].IsReleasedNow()) { // SPACE key released
                    state = GameState.Running;
                    startTime = System.currentTimeMillis(); // 게임 시작 시간 초기화
                    lastItemSpawnTime = startTime; // 마지막 아이템 생성 시간 초기화
                }
                break;

            case Running:
                if (inputs.buttons[4].IsPressedNow()) { // F1 key pressed
                    state = GameState.Info;
                } else {
                    handleRunningState();
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    if (elapsedTime >= gameDuration) {
                        state = GameState.Cleared;
                    }

                    long timeSinceLastItemSpawn = System.currentTimeMillis() - lastItemSpawnTime;
                    if (timeSinceLastItemSpawn >= itemSpawnInterval) {
                        spawnNewItem();
                        lastItemSpawnTime = System.currentTimeMillis(); // 마지막 아이템 생성 시간 갱신
                    }
                }
                break;

            case Info:
                if (inputs.buttons[4].IsReleasedNow()) { // F1 key released
                    if (gameRunning) {
                        state = GameState.Running;
                    } else {
                        state = GameState.Finished;
                    }
                }
                break;

            case Finished:
                if (inputs.buttons[5].IsPressedNow()) { // F2 key to restart
                    Initialize(); // 게임 초기화
                    state = GameState.Ready;
                } else if (inputs.buttons[4].IsPressedNow()) { // F1 key to show info
                    state = GameState.Info;
                }
                break;

            case Cleared:
                if (inputs.buttons[5].IsPressedNow()) { // F2 key to restart after clear
                    Initialize(); // 게임 초기화
                    state = GameState.Ready;
                } else if (inputs.buttons[4].IsPressedNow()) { // F1 key to show info
                    state = GameState.Info;
                }
                break;

            case Started:
                if (inputs.buttons[0].IsPressedNow()) { // SPACE key to start
                    state = GameState.Ready;
                }
                break;

            case Stopped:
                // 일시 중지 상태에서는 아무 것도 하지 않음
                break;
        }

        // 플레이어 업데이트
        player.update();

        // 생명 바 업데이트
        player.lifeBar.width = player.HP * lifebar_width / player_maxHP;
        if (player.HP < player_maxHP / 2) {
            player.lifeBar.image = images.GetImage("lifebar_red");
        } else {
            player.lifeBar.image = images.GetImage("lifebar_green");
        }

        return true;
    }

    private void handleRunningState() {
        if (inputs.buttons[1].IsPressedNow()) { // UP key
            player.jump();
        }
        if (inputs.buttons[2].IsPressedNow()) { // DOWN key
            player.slide();
        } else if (inputs.buttons[2].IsReleasedNow()) { // DOWN key released
            player.stopSliding();
        }
        if (inputs.buttons[3].IsPressedNow() && cheatModeAvailable) { // I key
            cheatModeActive = !cheatModeActive;
            player.invincible = true; // 무적 상태 활성화
            player.invincibleTime = 300; // 5초 동안 무적 상태
            for (Obstacle obstacle : obstacles) {
                obstacle.setCheatMode(cheatModeActive);
            }
            cheatModeAvailable = false; // 치트모드 사용 후 비활성화
            for (Item item : items) {
                item.active = false; // 아이템을 사용한 후 비활성화
            }
        }

        player.update();
        for (Obstacle obstacle : obstacles) {
            obstacle.update();
            if (!player.invincible && player.x < obstacle.x + obstacle.width && player.x + player.width > obstacle.x &&
                player.y < obstacle.y + obstacle.height && player.y + player.height > obstacle.y) {
                player.HP -= 80; // 충돌 시 HP 감소
                
                if (player.HP <= 0) {
                    player.HP = 0;
                    gameRunning = false;
                    state = GameState.Finished;
                } else {
                    // 5초 동안 무적 모드 활성화
                    player.invincible = true;
                    player.invincibleTime = 300; // 5초 동안 무적 모드
                }
            }
        }

        // 아이템과 충돌 체크
        for (Item item : items) {
            item.update();
            if (item.active && player.x < item.x + item.width && player.x + player.width > item.x &&
                player.y < item.y + item.height && player.y + player.height > item.y) {
                score += 10; // 아이템 획득 시 점수 증가
                cheatModeAvailable = true;
                item.active = false; // 아이템 비활성화
            }
        }

        // 게임 종료 체크
        if (player.HP <= 0) {
            state = GameState.Finished;
        }
        
        if (gameRunning) {
            score++;
        }
    }

    private void spawnNewItem() {
        int randomIndex = new Random().nextInt(items.size());
        items.get(randomIndex).x = 800;
        items.get(randomIndex).y = new Random().nextInt(250) + 150;
        items.get(randomIndex).active = true; // 새로운 아이템을 랜덤한 위치에 활성화
    }

    @Override
    public void Draw(long timeStamp) {
        // 그리기 작업 시작 - 이 메서드는 Draw()의 가장 위에서 항상 호출해 주어야 함
        BeginDraw();
        ClearScreen();

        if (state == GameState.Info) {
            drawInfo();
        } else if (state == GameState.Cleared) {
            drawClearScreen();
        } else {
            player.lifeBar.Draw(g);
            player.Draw(g);
            for (Obstacle obstacle : obstacles) {
                obstacle.Draw(g);
            }
            for (Item item : items) {
                if (item.active) {
                    item.Draw(g);
                }
            }

            g.drawString("Score: " + score, 10, 80);
            if (state == GameState.Finished) {
                g.drawString("Game Over, Press F2 to Restart", 400, 300);
            } else if (cheatModeAvailable) {
                g.drawString("Press 'I' to activate cheat mode", 400, 30);
            } else if (state == GameState.Started) {
                g.drawString("Press SPACE to Start", 10, 50);
            } else if (state == GameState.Ready) {
                g.drawString("Release SPACE to Run", 10, 50);
            }
        }

        EndDraw();
    }

    private void drawInfo() {
        String[] infoText = {
            "Cookie Run Game Info",
            "Use arrow keys to control the player.",
            "Press 'I' to toggle cheat mode.",
            "Press 'F1' to view this info.",
            "Press 'SPACE' to start the game.",
            "Avoid obstacles and collect items.",
            "Score as many points as possible!",
            "2391017, 인공지능학과 이윤지 올림.. 늦게 제출하여 죄송하다는 말씀드립니다"
        };

        int yPosition = 150; // 텍스트 시작 y 위치 (화면 중앙에 배치하기 위해 조정)
        int xPosition = getWidth() / 2; // 화면 중앙의 x 위치
        
        for (String line : infoText) {
            int stringWidth = g.getFontMetrics().stringWidth(line);
            g.drawString(line, xPosition - stringWidth / 2, yPosition);
            yPosition += 20; // 각 줄 간격
        }
    }

    private void drawClearScreen() {
        String clearText = "Congratulations! You have cleared the game! Press F2 key to restart";
        int xPosition = getWidth() / 2;
        int yPosition = getHeight() / 2;
        int stringWidth = g.getFontMetrics().stringWidth(clearText);
        g.drawString(clearText, xPosition - stringWidth / 2, yPosition);
    }

    public static void main(String[] args) {
        GameFrameSettings settings = new GameFrameSettings();
        settings.window_title = "Cookie Run Game";
        settings.canvas_width = 800;
        settings.canvas_height = 600;
        settings.gameLoop_interval_ns = 16666666;  // 약 60FPS
        settings.gameLoop_use_virtualTimingMode = true;
        settings.numberOfButtons = 6;

        CookieRunGameFrame window = new CookieRunGameFrame(settings);
        window.setVisible(true);
    }
}
