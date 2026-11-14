package cn.nukkit.entity.item;

/**
 * 基岩版船的划桨控制模型，用于 1.21.130+ 客户端的服务端权威模拟。
 * Bedrock Edition boat control model for the server-side simulation of 1.21.130+ clients.
 * <p>
 * Adapted from Boar (<a href="https://github.com/opencollab-incubator/Boar">Boar</a>) — reverse-engineered
 * constants of the client's BoatMoveFrictionSystem, BoatPaddleInputSystem and BoatMoveControlSystem.
 */
final class BoatPaddlePhysics {

    static final float WATER_FRICTION = 0.9f;
    static final float AIR_FRICTION = 1.0f;
    static final float AIR_BLOCK_FRICTION = 0.9f;
    static final float PADDLE_FORCE = 0.01375f;
    static final float PADDLE_SPEED = 3.0f;
    static final float PADDLE_SPEED_DECAY = 0.1f;
    static final int PADDLE_PULSE_TICKS = 10;
    static final float TORQUE_ARM = 3.0f;
    static final float SLOW_SPEED_SQR = 0.010000001f;
    static final float SLOW_TURN_BOOST = 1.6f;
    static final float TURN_SCALE = 10.0f;
    static final float BUTTON_MIN_SPEED = 2.5f;
    static final float BUTTON_SPEED_DECAY = 0.05f;
    static final float BUTTON_STOP_SPEED = 0.01f;

    private static final float DEGREE_TO_RAD = (float) (Math.PI / 180.0);

    static final class Paddle {
        // 客户端构造时归零，rowStart 初值为 0 而不是 -1。
        // The client constructor zeroes the component, so rowStart starts at 0 rather than -1.
        int rowStart;
        int lastRowStart;
        float speed;
    }

    record TickResult(float motionX, float motionZ, float yawVelocity, float firstPaddleSpeed, float secondPaddleSpeed) {
    }

    private final Paddle firstPaddle = new Paddle();
    private final Paddle secondPaddle = new Paddle();
    private int paddleTick;
    private float yawVelocity;

    /**
     * 换骑手时清空桨与角速度，回到刚构造时的状态。
     * Clears paddle and angular-velocity state back to the freshly constructed state when the rider changes.
     */
    void reset() {
        this.firstPaddle.rowStart = 0;
        this.firstPaddle.lastRowStart = 0;
        this.firstPaddle.speed = 0.0f;
        this.secondPaddle.rowStart = 0;
        this.secondPaddle.lastRowStart = 0;
        this.secondPaddle.speed = 0.0f;
        this.paddleTick = 0;
        this.yawVelocity = 0.0f;
    }

    /**
     * 推进一 tick。模拟顺序：摩擦（速度与角速度）→ 桨速 → 推力/力矩 → 转向 → 速度。
     * Advances one tick: friction on velocity and angular velocity, paddle speeds, thrust/torque, yaw, velocity.
     */
    TickResult tick(boolean buttons, double inputX, double inputY, boolean up,
                    boolean buttonLeft, boolean buttonRight,
                    float friction, boolean canPaddle,
                    float motionX, float motionZ, float yaw) {
        float vx = motionX * friction;
        float vz = motionZ * friction;
        float yawVelocity = this.yawVelocity * friction;

        if (buttons) {
            // 按钮不镜像：PADDLE_LEFT 驱动第一支桨。
            // Buttons are not mirrored: PADDLE_LEFT drives the first paddle.
            this.updateButtonPaddleSpeed(this.firstPaddle, buttonLeft);
            this.updateButtonPaddleSpeed(this.secondPaddle, buttonRight);
        } else {
            // 轴向输入镜像映射：x=-1（右移）驱动第一支桨。
            // The analog names are mirrored: x=-1 (strafe right) drives the first paddle.
            float x = (float) inputX;
            float y = up ? 1.0f : clamp((float) inputY, -1.0f, 1.0f);
            // 客户端可发送 NaN/Infinity；按零输入处理，避免污染速度与航向。
            // Clients can send NaN/Infinity; treat them as zero input so they cannot poison velocity or heading.
            if (!Float.isFinite(x) || !Float.isFinite(y)) {
                x = 0.0f;
                y = 0.0f;
            }
            // 网络路径已钳制；这里兜住直接调用者的越界输入。
            // The network path already clamps; this bounds direct callers.
            x = clamp(x, -1.0f, 1.0f);
            float magnitude = (float) Math.sqrt(x * x + y * y);
            if (y < 0) {
                magnitude *= -0.15f;
                x = -x;
            }
            float firstIntent = Math.max(0.0f, 1.0f - Math.max(x, 0.0f)) * magnitude;
            float secondIntent = (1.0f + Math.max(-1.0f, Math.min(x, 0.0f))) * magnitude;
            this.updatePaddleSpeed(this.firstPaddle, firstIntent);
            this.updatePaddleSpeed(this.secondPaddle, secondIntent);
        }
        this.paddleTick++;

        // 空气/干地中不划桨，但桨动画与计时照常推进。
        // Thrust is off in dry air, while the paddle animation and timing still advance.
        float firstForce = this.paddleForce(this.firstPaddle);
        float secondForce = this.paddleForce(this.secondPaddle);
        if (!canPaddle) {
            firstForce = 0.0f;
            secondForce = 0.0f;
        }

        float thrust = firstForce + secondForce;
        float torque = firstForce * TORQUE_ARM + secondForce * -TORQUE_ARM;
        if (vx * vx + vz * vz < SLOW_SPEED_SQR && torque != 0.0f) {
            thrust *= friction;
            torque *= SLOW_TURN_BOOST;
        }

        yawVelocity = (torque * TURN_SCALE + yawVelocity) * friction;
        float newYaw = yaw + yawVelocity;
        float angle = (90.0f - newYaw) * DEGREE_TO_RAD;
        vx = (vx + thrust * (float) Math.sin(angle)) * friction;
        vz = (vz + thrust * (float) Math.cos(angle)) * friction;

        this.yawVelocity = yawVelocity;
        return new TickResult(vx, vz, yawVelocity, this.firstPaddle.speed, this.secondPaddle.speed);
    }

    private void updatePaddleSpeed(Paddle paddle, float intent) {
        int tick = this.paddleTick;
        if (intent == 0.0f) {
            paddle.speed = 0.0f;
        } else if (tick - paddle.rowStart >= PADDLE_PULSE_TICKS) {
            paddle.speed = intent * PADDLE_SPEED;
            paddle.rowStart = tick;
        } else {
            paddle.speed = Math.signum(intent) * Math.max(0.0f, Math.abs(intent * PADDLE_SPEED) - PADDLE_SPEED_DECAY);
        }
    }

    private void updateButtonPaddleSpeed(Paddle paddle, boolean held) {
        int tick = this.paddleTick;
        if (held) {
            if (paddle.rowStart < 0) {
                paddle.speed = tick - paddle.lastRowStart >= PADDLE_PULSE_TICKS
                        ? PADDLE_SPEED : Math.max(BUTTON_MIN_SPEED, paddle.speed - BUTTON_SPEED_DECAY);
                paddle.rowStart = tick;
            } else {
                paddle.speed = tick - paddle.rowStart < PADDLE_PULSE_TICKS
                        ? PADDLE_SPEED : Math.max(BUTTON_MIN_SPEED, paddle.speed - BUTTON_SPEED_DECAY);
            }
        } else {
            if (paddle.rowStart >= 0) {
                paddle.lastRowStart = paddle.rowStart;
                paddle.rowStart = -1;
            }
            paddle.speed = paddle.speed <= BUTTON_STOP_SPEED ? 0.0f : paddle.speed * 0.5f;
        }
    }

    private float paddleForce(Paddle paddle) {
        float force = paddle.speed * PADDLE_FORCE;
        if (force == 0.0f) {
            paddle.rowStart = -1;
            return 0.0f;
        }
        return force;
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }
}
