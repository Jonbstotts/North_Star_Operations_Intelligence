package com.wtm.ui;

import com.wtm.security.AuditService;
import com.wtm.security.UserAccount;
import com.wtm.security.UserService;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * North Star startup experience.
 *
 * v1.1.4 replaces the static splash/progress bar with the approved loading
 * animation supplied for North Star. The animation plays exactly once. The
 * final frame is then held indefinitely while the secure login card fades in
 * over the final portion of the animation. A later looping idle animation can
 * be substituted without changing the authentication flow.
 */
public final class NorthStarSplashScreen extends JFrame {
    private static final int FRAME_COUNT = 97;
    private static final int FRAME_DELAY_MS = 83; // ~12 fps, 8.05 seconds total
    private static final int LOGIN_FADE_START_FRAME = 75;
    private static final int LOGIN_FADE_DURATION_MS = 1650;

    private final StartupSurface surface = new StartupSurface();
    private final Timer animationTimer;
    private final Timer fadeTimer;

    private int frameIndex;
    private long fadeStartedAt = -1L;
    private boolean animationFinished;
    private boolean loginRequested;
    private boolean loginFullyVisible;
    private Runnable afterAnimation;

    public NorthStarSplashScreen() {
        /*
         * v1.1.8: use a real undecorated JFrame for the startup/authentication
         * surface. On macOS a JWindow can render correctly yet still fail to
         * become a key window for native keyboard/mouse focus. An undecorated
         * JFrame keeps the identical visual treatment while participating in
         * the normal Swing focus cycle.
         */
        super("North Star Operations Intelligence");
        setUndecorated(true);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setContentPane(surface);

        /*
         * Keep the supplied animation at its original aspect ratio and reserve
         * the lower authentication region exactly as in v1.1.7.
         */
        setSize(1000, 820);
        setLocationRelativeTo(null);
        setBackground(Color.BLACK);
        setFocusableWindowState(true);
        setAutoRequestFocus(true);
        setFocusTraversalKeysEnabled(true);
        setAlwaysOnTop(true);
        ApplicationBrand.applyWindowIcon(this);

        animationTimer = new Timer(FRAME_DELAY_MS, e -> advanceAnimation());
        animationTimer.setCoalesce(true);

        fadeTimer = new Timer(33, e -> advanceLoginFade());
        fadeTimer.setCoalesce(true);

        animationTimer.start();
    }

    /**
     * Kept for startup-loader compatibility. The supplied video is now the
     * visible progress experience, so no synthetic progress bar is painted.
     */
    public void updateProgress(int value, String message) {
        // Intentionally no visual progress override: preserve supplied video.
    }

    /**
     * Requests the secure startup login. The card begins fading in during the
     * final ~1.6 seconds of the animation, or immediately if configuration
     * loading finishes after the video has already reached its final frame.
     */
    public void requestLogin(
            String suggestedUsername,
            Consumer<UserAccount> onAuthenticated,
            Runnable onCancel
    ) {
        Runnable configure = () -> {
            loginRequested = true;
            surface.login.configure(
                    suggestedUsername,
                    onAuthenticated,
                    onCancel
            );
            surface.login.setVisible(true);

            /*
             * The controls remain disabled only until the visible portion of
             * the fade begins. Explicitly make the frame a focus owner now so
             * mouse clicks will be delivered to the text components.
             */
            setFocusableWindowState(true);
            setAutoRequestFocus(true);

            if (isVisible()) {
                toFront();
                requestFocusInWindow();
            }

            if (animationFinished || frameIndex >= LOGIN_FADE_START_FRAME) {
                beginLoginFade();
            }
        };

        if (SwingUtilities.isEventDispatchThread()) configure.run();
        else SwingUtilities.invokeLater(configure);
    }

    /**
     * Runs an action after the one-shot animation reaches its final frame.
     * Useful when login is disabled or when first-run administrator migration
     * must be shown after the branded startup experience.
     */
    public void afterAnimation(Runnable action) {
        Runnable set = () -> {
            if (animationFinished) action.run();
            else afterAnimation = action;
        };

        if (SwingUtilities.isEventDispatchThread()) set.run();
        else SwingUtilities.invokeLater(set);
    }

    /**
     * Switches the lower startup region from authentication to a branded
     * loading state while the Operations Workspace is being constructed.
     *
     * The completed North Star animation remains visible above it so startup
     * never falls through to a blank desktop or partially painted window.
     */
    public void beginWorkspaceLaunch(String message) {
        Runnable begin = () -> {
            surface.login.beginLaunching(
                    message == null || message.isBlank()
                            ? "Preparing Operations Workspace..."
                            : message
            );
            surface.login.setVisible(true);
            surface.login.setAlpha(1f);
            surface.repaint();
            repaint();
        };

        if (SwingUtilities.isEventDispatchThread()) begin.run();
        else SwingUtilities.invokeLater(begin);
    }

    public void closeSplash() {
        Runnable close = () -> {
            animationTimer.stop();
            fadeTimer.stop();
            surface.login.clearPassword();
            setVisible(false);
            dispose();
        };

        if (SwingUtilities.isEventDispatchThread()) close.run();
        else SwingUtilities.invokeLater(close);
    }

    private void advanceAnimation() {
        if (frameIndex < surface.frames.size() - 1) {
            frameIndex++;
            surface.animationFrame = frameIndex;
            surface.repaint();

            if (loginRequested && frameIndex >= LOGIN_FADE_START_FRAME) {
                beginLoginFade();
            }
            return;
        }

        animationTimer.stop();
        animationFinished = true;
        surface.animationFrame = Math.max(0, surface.frames.size() - 1);
        surface.repaint();

        if (loginRequested) beginLoginFade();

        if (afterAnimation != null) {
            Runnable task = afterAnimation;
            afterAnimation = null;
            task.run();
        }
    }

    private void beginLoginFade() {
        if (loginFullyVisible || fadeStartedAt >= 0L) return;
        fadeStartedAt = System.currentTimeMillis();
        fadeTimer.start();
    }

    private void advanceLoginFade() {
        if (fadeStartedAt < 0L) return;

        float alpha = Math.min(
                1f,
                (System.currentTimeMillis() - fadeStartedAt)
                        / (float) LOGIN_FADE_DURATION_MS
        );

        surface.login.setAlpha(easeOut(alpha));

        /*
         * Make the controls interactive once they are clearly visible. Waiting
         * until the final few fade frames made the UI look ready while still
         * ignoring clicks.
         */
        surface.login.setInteractionEnabled(alpha >= 0.20f);
        surface.repaint();

        if (alpha >= 1f) {
            fadeTimer.stop();
            loginFullyVisible = true;
            surface.login.setInteractionEnabled(true);

            SwingUtilities.invokeLater(() -> {
                /*
                 * Bring the JWindow into the active focus cycle before asking
                 * the text field for focus. This is especially important on
                 * macOS where a decorative JWindow may otherwise remain
                 * visible but non-key.
                 */
                toFront();
                requestFocus();
                requestFocusInWindow();
                surface.login.focusInitialField();
            });
        }
    }

    private static float easeOut(float t) {
        float inv = 1f - Math.max(0f, Math.min(1f, t));
        return 1f - inv * inv * inv;
    }

    private static final class StartupSurface extends JPanel {
        private final List<BufferedImage> frames = loadFrames();
        private final FadingLoginPanel login = new FadingLoginPanel();
        private int animationFrame;

        private StartupSurface() {
            setOpaque(true);
            setBackground(new Color(3, 8, 14));
            /*
             * v1.1.6 stable two-region startup layout:
             * - animation remains in its own aspect-ratio-preserving region
             * - a dedicated lower login region is reserved from frame one
             * - the login fades into that reserved space, never over the logo
             *
             * Reserving the lower region from startup avoids any visual jump
             * when authentication becomes available.
             */
            setLayout(new BorderLayout(0, 0));

            JPanel animationRegion = new JPanel() {
                @Override
                protected void paintComponent(Graphics graphics) {
                    super.paintComponent(graphics);
                    if (frames.isEmpty()) return;

                    BufferedImage image = frames.get(
                            Math.max(0, Math.min(animationFrame, frames.size() - 1))
                    );

                    Graphics2D g = (Graphics2D) graphics.create();
                    try {
                        g.setRenderingHint(
                                RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_BICUBIC
                        );
                        g.setRenderingHint(
                                RenderingHints.KEY_RENDERING,
                                RenderingHints.VALUE_RENDER_QUALITY
                        );

                        int w = getWidth();
                        int h = getHeight();
                        double scale = Math.min(
                                w / (double) image.getWidth(),
                                h / (double) image.getHeight()
                        );
                        int dw = (int) Math.round(image.getWidth() * scale);
                        int dh = (int) Math.round(image.getHeight() * scale);
                        int x = (w - dw) / 2;
                        int y = (h - dh) / 2;

                        g.setColor(new Color(3, 8, 14));
                        g.fillRect(0, 0, w, h);
                        g.drawImage(image, x, y, dw, dh, null);
                    } finally {
                        g.dispose();
                    }
                }
            };
            animationRegion.setOpaque(true);
            animationRegion.setBackground(new Color(3, 8, 14));

            /*
             * Keep the authentication footprint reserved even before the fade
             * begins. The panel itself is initially invisible/alpha 0, but
             * the SOUTH region remains present, leaving a clean black landing
             * area beneath the completed animation.
             */
            login.setVisible(false);
            login.setPreferredSize(new Dimension(1000, 235));
            login.setMinimumSize(new Dimension(700, 235));
            login.setMaximumSize(new Dimension(Integer.MAX_VALUE, 235));

            add(animationRegion, BorderLayout.CENTER);
            add(login, BorderLayout.SOUTH);
        }

        private static List<BufferedImage> loadFrames() {
            List<BufferedImage> result = new ArrayList<>(FRAME_COUNT);
            ClassLoader loader = NorthStarSplashScreen.class.getClassLoader();

            for (int i = 1; i <= FRAME_COUNT; i++) {
                String name = String.format(
                        "northstar/startup_frames/frame_%03d.jpg",
                        i
                );

                try (InputStream in = loader.getResourceAsStream(name)) {
                    if (in == null) break;
                    BufferedImage image = ImageIO.read(in);
                    if (image != null) result.add(image);
                } catch (Exception ignored) {
                    break;
                }
            }

            if (result.isEmpty()) {
                BufferedImage fallback = NorthStarBrand.splashArtwork();
                if (fallback != null) result.add(fallback);
            }
            return result;
        }
    }

    /**
     * Compact authentication card living directly under the held final frame.
     * The complete panel is alpha-composited so text, controls, borders and
     * icons fade together rather than appearing in separate stages.
     */
    private static final class FadingLoginPanel extends JPanel {
        private static final String LOGIN_CARD = "login";
        private static final String LAUNCH_CARD = "launch";

        private final NorthStarLoginField username =
                new NorthStarLoginField(NorthStarLoginField.Kind.USERNAME);
        private final NorthStarLoginField password =
                new NorthStarLoginField(NorthStarLoginField.Kind.PASSWORD);
        private final NorthStarPrimaryButton signIn =
                new NorthStarPrimaryButton("Sign In");
        private final JLabel status =
                new JLabel(" ", SwingConstants.CENTER);

        private final CardLayout modeLayout = new CardLayout();
        private final JPanel modeHost = new JPanel(modeLayout);
        private final JLabel launchMessage = new JLabel(
                "Preparing Operations Workspace...",
                SwingConstants.CENTER
        );
        private final JProgressBar launchProgress = new JProgressBar();

        private float alpha;
        private Consumer<UserAccount> onAuthenticated;
        private Runnable onCancel;
        private Timer lockoutTimer;

        private FadingLoginPanel() {
            setOpaque(false);
            setLayout(new GridBagLayout());

            /*
             * Center one bounded content surface vertically and horizontally
             * inside the reserved lower startup region. This keeps the login
             * visually balanced instead of pinning it to the top edge.
             */
            modeHost.setOpaque(false);
            modeHost.setPreferredSize(new Dimension(780, 150));
            modeHost.setMaximumSize(new Dimension(900, 150));

            modeHost.add(buildLoginCard(), LOGIN_CARD);
            modeHost.add(buildLaunchCard(), LAUNCH_CARD);
            modeLayout.show(modeHost, LOGIN_CARD);

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.weightx = 1.0;
            gbc.weighty = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.CENTER;
            gbc.insets = new Insets(0, 105, 0, 105);
            add(modeHost, gbc);

            setInteractionEnabled(false);
        }

        private JPanel buildLoginCard() {
            JPanel card = new JPanel();
            card.setOpaque(false);
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

            JPanel headingRow = new JPanel(new BorderLayout());
            headingRow.setOpaque(false);
            headingRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

            JLabel welcome = new JLabel("Secure Sign In");
            welcome.setForeground(Theme.text());
            welcome.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));

            JLabel helper = new JLabel(
                    "Sign in to continue to North Star Operations Intelligence."
            );
            helper.setForeground(Theme.muted());
            helper.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));

            headingRow.add(welcome, BorderLayout.WEST);
            headingRow.add(helper, BorderLayout.EAST);

            JPanel fields = new JPanel(new GridLayout(1, 3, 10, 0));
            fields.setOpaque(false);
            fields.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));

            username.textField().putClientProperty(
                    "northstar.placeholder",
                    "Username"
            );
            password.textField().putClientProperty(
                    "northstar.placeholder",
                    "Password"
            );

            signIn.addActionListener(e -> attemptLogin());
            password.textField().addActionListener(e -> attemptLogin());

            fields.add(username);
            fields.add(password);
            fields.add(signIn);

            status.setForeground(Theme.danger());
            status.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            status.setAlignmentX(Component.CENTER_ALIGNMENT);

            card.add(headingRow);
            card.add(Box.createVerticalStrut(10));
            card.add(fields);
            card.add(Box.createVerticalStrut(7));
            card.add(status);
            return card;
        }

        private JPanel buildLaunchCard() {
            JPanel card = new JPanel(new GridBagLayout());
            card.setOpaque(false);

            JPanel stack = new JPanel();
            stack.setOpaque(false);
            stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));

            JLabel title = new JLabel(
                    "Secure session established",
                    SwingConstants.CENTER
            );
            title.setForeground(Theme.text());
            title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            title.setAlignmentX(Component.CENTER_ALIGNMENT);

            launchMessage.setForeground(Theme.muted());
            launchMessage.setFont(
                    new Font(Font.SANS_SERIF, Font.PLAIN, 11)
            );
            launchMessage.setAlignmentX(Component.CENTER_ALIGNMENT);

            launchProgress.setIndeterminate(true);
            launchProgress.setBorderPainted(false);
            launchProgress.setOpaque(false);
            launchProgress.setPreferredSize(new Dimension(420, 6));
            launchProgress.setMaximumSize(new Dimension(520, 6));
            launchProgress.setAlignmentX(Component.CENTER_ALIGNMENT);

            stack.add(title);
            stack.add(Box.createVerticalStrut(7));
            stack.add(launchMessage);
            stack.add(Box.createVerticalStrut(16));
            stack.add(launchProgress);

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.weightx = 1;
            gbc.weighty = 1;
            gbc.anchor = GridBagConstraints.CENTER;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            card.add(stack, gbc);
            return card;
        }

        private void configure(
                String suggestedUsername,
                Consumer<UserAccount> authenticated,
                Runnable cancelled
        ) {
            onAuthenticated = authenticated;
            onCancel = cancelled;
            username.setText(suggestedUsername == null ? "" : suggestedUsername);
            modeLayout.show(modeHost, LOGIN_CARD);
        }

        private void beginLaunching(String message) {
            setInteractionEnabled(false);
            status.setText(" ");
            launchMessage.setText(message);
            modeLayout.show(modeHost, LAUNCH_CARD);
            revalidate();
            repaint();
        }

        private void setAlpha(float value) {
            alpha = Math.max(0f, Math.min(1f, value));
            repaint();
        }

        private void setInteractionEnabled(boolean enabled) {
            username.setEnabled(enabled);
            password.setEnabled(enabled);
            signIn.setEnabled(enabled);
        }

        private void focusInitialField() {
            if (username.text().isBlank()) {
                username.textField().requestFocusInWindow();
            } else {
                password.textField().requestFocusInWindow();
            }
        }

        private void clearPassword() {
            password.clear();
            if (lockoutTimer != null) lockoutTimer.stop();
        }

        private void attemptLogin() {
            int delay = UserService.lockoutSecondsRemaining();
            if (delay > 0) {
                startDelay(delay);
                return;
            }

            char[] supplied = password.password();
            try {
                UserAccount account =
                        UserService.authenticate(username.text(), supplied);
                if (account != null) {
                    AuditService.record(
                            account.username(),
                            "Successful startup login"
                    );
                    setInteractionEnabled(false);
                    beginLaunching("Preparing Operations Workspace...");
                    if (onAuthenticated != null) onAuthenticated.accept(account);
                    return;
                }
            } finally {
                Arrays.fill(supplied, '\0');
                password.clear();
            }

            delay = UserService.lockoutSecondsRemaining();
            if (delay > 0) startDelay(delay);
            else {
                status.setText("Incorrect username or password.");
                Toolkit.getDefaultToolkit().beep();
            }
        }

        private void startDelay(int seconds) {
            setInteractionEnabled(false);
            final int[] remaining = {Math.max(1, seconds)};
            status.setText(
                    "Too many attempts. Try again in "
                            + remaining[0] + " seconds."
            );

            if (lockoutTimer != null) lockoutTimer.stop();
            lockoutTimer = new Timer(1000, e -> {
                remaining[0]--;
                if (remaining[0] <= 0) {
                    ((Timer) e.getSource()).stop();
                    setInteractionEnabled(true);
                    status.setText(" ");
                    password.textField().requestFocusInWindow();
                } else {
                    status.setText(
                            "Too many attempts. Try again in "
                                    + remaining[0] + " seconds."
                    );
                }
            });
            lockoutTimer.start();
        }

        @Override
        public void paint(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setComposite(
                        AlphaComposite.SrcOver.derive(alpha)
                );

                g.setColor(new Color(6, 11, 17, 248));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(new Color(
                        Theme.border().getRed(),
                        Theme.border().getGreen(),
                        Theme.border().getBlue(),
                        120
                ));
                g.drawLine(0, 0, getWidth(), 0);

                super.paint(g);
            } finally {
                g.dispose();
            }
        }
    }
}
