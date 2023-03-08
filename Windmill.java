
import java.nio.*;
import javax.swing.*;
import java.lang.Math;
import java.lang.reflect.Array;
import static com.jogamp.opengl.GL4.*;
import com.jogamp.opengl.*;
import com.jogamp.opengl.FBObject.Colorbuffer;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.math.Matrix4;
import com.jogamp.opengl.util.*;
import com.jogamp.common.nio.Buffers;

import org.joml.*;

public class Windmill extends JFrame implements GLEventListener {

    private static final int WINDOW_WIDTH = 1000, WINDOW_HEIGHT = 600;
    private static final String WINDOW_TITLE = "Windmill";

    private static final String VERTEX_SHADER_FILE = "windmill-vertex.glsl",
            FRAGMENT_SHADER_FILE = "windmill-fragment.glsl";

    private final FloatBuffer scratchBuffer = Buffers.newDirectFloatBuffer(16);

    private GL4 gl;

    private GLCanvas glCanvas;
    private int renderingProgram; // Shader Id
    private int[] vao = new int[1];
    private int[] vbo = new int[4];
    private float cameraX, cameraY, cameraZ;

    private int number_of_blades;
    private float period_of_blades;
    private float period_of_camera;
    private long startTime;
    private long elapsedTime;
    private double frequencyBlades;
    private double frequencyCamara;
    private double frameRotationBlade;
    private double frameRotationCamara;

    // allocate variables for display() function
    private Matrix4f viewMatrix = new Matrix4f();
    private Matrix4f perspectiveMatrix = new Matrix4f();
    private float aspectRatio;

    // Model Matrices for
    private Matrix4f modelViewMatrix = new Matrix4f();
    private Matrix4f modelMatrixNS = new Matrix4f(); // North Sound walls model matrix
    private Matrix4f modelMatrixEW = new Matrix4f(); // East west walls model matrix
    private Matrix4f modelMatrixPry = new Matrix4f(); // Pryimid model matrix
    private Matrix4f modelMatrixBlade = new Matrix4f();

    // Display Ids for shaders
    int mv_matrixID;
    int p_matrixID;

    // Fragment color
    int fragmentColor;

    // Colors defined for each part of the windmil
    float NS_wColor[] = { 0.0f, 1.0f, 0.0f, 1.0f }; // Green wall color for the north south facing walls
    float EW_wColor[] = { 1.0f, 0.0f, 0.0f, 1.0f }; // Red wall color for the east west facing walls
    float Roof_Color[] = { 0.99609375f, 0.83984375f, 0.0f, 1.0f }; // Gold Yellow color for the triangle cealing
    float Blade_Color[] = { (96 / 256f), (96 / 256f), (86 / 256f), 1.0f }; // Gray color for blades

    /**
     * Constructor for the Windmill
     * 
     * @param num_blades
     * @param period_blades
     * @param period_cam
     */
    public Windmill(int num_blades, float period_blades, float period_cam) {
        number_of_blades = num_blades;
        period_of_blades = period_blades;
        period_of_camera = period_cam;

        setTitle(WINDOW_TITLE);
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        glCanvas = new GLCanvas();
        glCanvas.addGLEventListener(this);
        this.add(glCanvas);
        this.setVisible(true);
        setLocationRelativeTo(null);

        Animator animator = new Animator(glCanvas);
        animator.start();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    public static void main(String[] args) {

        // Command line argument parse and processing.
        // Any error from parsing will throw an excpetion.
        try {
            int num_blades = Integer.parseInt(args[0]);
            float period_blades = Float.parseFloat(args[1]);
            float period_cam = Float.parseFloat(args[2]);

            if (num_blades <= 0) {
                throw new Exception();
            }

            new Windmill(num_blades, period_blades, period_cam); // Run program!

        } catch (Exception e) {
            System.out.println(
                    "Sorry, you arguments were not readable by the program. Try the following: \n (int >= 1, float, float)");
        }
    }

    @Override
    public void init(GLAutoDrawable drawable) {

        this.gl = (GL4) GLContext.getCurrentGL();
        renderingProgram = Utils.createShaderProgram(VERTEX_SHADER_FILE, FRAGMENT_SHADER_FILE); // Ready the program.
                                                                                                // Compiling and such
        startTime = System.currentTimeMillis(); // Set the start time

        // Inital Camara position
        cameraX = 0.0f;
        cameraY = 1.5f;
        cameraZ = 9.0f;

        // Put model verticies into buffers
        setupVertices(); // Set up vbos

        // Set up inital prospective matrix:
        aspectRatio = (float) glCanvas.getWidth() / (float) glCanvas.getHeight();

        // prospective
        viewMatrix.setLookAt(cameraX, cameraY, cameraZ, 0, 0, 0, 0, 1, 0); // Calculate inintal look at matrix

        // Matrices for model view matrix:
        Matrix4f translate = new Matrix4f().translate(0, 0, 0); // Default
        Matrix4f translateRoof = new Matrix4f().translate(0, 3, 0);
        Matrix4f translateBlade = new Matrix4f().translate(0.0f, 1.5f, 1.01f);
        Matrix4f rotation = new Matrix4f().rotateY((float) Math.toRadians(0.0f)); // Default
        Matrix4f rotation90 = new Matrix4f().rotateY((float) Math.toRadians(90.0f)); // Default
        Matrix4f scaleWalls = new Matrix4f().scale(1, 2, 1);

        // Set up model view matrices for each of the models in the windmill: V * T * R
        // * S
        modelMatrixNS.mul(translate).mul(rotation).mul(scaleWalls);
        modelMatrixEW.mul(translate).mul(rotation90).mul(scaleWalls);
        modelMatrixPry.mul(translateRoof).mul(rotation);
        modelMatrixBlade.mul(translateBlade).mul(rotation);

        // Get and save the id locations for each of the shader locaiton ids
        this.mv_matrixID = gl.glGetUniformLocation(renderingProgram, "mv_matrix"); // Save model matrix id
        this.p_matrixID = gl.glGetUniformLocation(renderingProgram, "p_matrix"); // Save model matrix id
        this.fragmentColor = gl.glGetUniformLocation(renderingProgram, "incolor"); // Save color input locaiton

        // Get the frequency for blades
        if (period_of_blades == 0.0) {
            this.frequencyBlades = 0;
        } else {
            this.frequencyBlades = -((double) ((Math.PI * 2) / period_of_blades)); // Algle per period of time
        }

        // Get the frequency for camara rotation
        if (period_of_camera == 0.0) {
            this.period_of_camera = 0;
        } else {
            this.frequencyCamara = ((double) ((Math.PI * 2) / period_of_camera)); // Algle per period of time
        }

        // Enabled z buffer (once)
        this.gl.glEnable(GL_DEPTH_TEST);
        this.gl.glDepthFunc(GL_LEQUAL);
    }

    @Override
    public void display(GLAutoDrawable arg0) {
        // Reset color buffers to default
        this.gl.glClear(GL_COLOR_BUFFER_BIT); // clear screen
        this.gl.glClear(GL_DEPTH_BUFFER_BIT); // clear Z-buffer
        this.gl.glUseProgram(renderingProgram); // Shader Id to use
        this.gl.glClearColor(0.1171875f, 0.43359375f, 0.8984375f, 1); // Background color (blueish)

        // Get the amount of time passed since the last frame
        this.elapsedTime = System.currentTimeMillis() - startTime;
        float timeMillis = (elapsedTime / 1000.0f);

        // Get the rotation since last frame
        this.frameRotationBlade = (float) (timeMillis * frequencyBlades);
        this.frameRotationCamara = (float) (timeMillis * frequencyCamara);

        // Apply rotation to camara postion on the Y axis to spin around the windmill
        viewMatrix.rotate((float) frameRotationCamara, 0.0f, 1.0f, 0.0f);

        // Assign model view matrix to rendering program
        viewMatrix.mul(modelMatrixNS, modelViewMatrix); // Save in to model matrix

        this.gl.glUniformMatrix4fv(mv_matrixID, 1, false, modelViewMatrix.get(scratchBuffer));
        this.gl.glUniformMatrix4fv(p_matrixID, 1, false, perspectiveMatrix.get(scratchBuffer));
        this.gl.glProgramUniform3f(renderingProgram, fragmentColor, NS_wColor[0], NS_wColor[1], NS_wColor[2]);

        this.gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[0]);
        this.gl.glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
        this.gl.glEnableVertexAttribArray(0);

        this.gl.glDrawArrays(GL_TRIANGLES, 0, 36); // Draw Green Wall

        // Update model view matrix vbo
        viewMatrix.mul(modelMatrixEW, modelViewMatrix); // Calculate model view matrix

        this.gl.glUniformMatrix4fv(mv_matrixID, 1, false, modelViewMatrix.get(scratchBuffer));
        this.gl.glProgramUniform3f(renderingProgram, fragmentColor, EW_wColor[0], EW_wColor[1], EW_wColor[2]);
        this.gl.glDrawArrays(GL_TRIANGLES, 0, 36); // Draw Red Wall

        // Prime load yellow pyrimid vbo
        this.gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[1]);
        this.gl.glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
        this.gl.glEnableVertexAttribArray(0);

        viewMatrix.mul(modelMatrixPry, modelViewMatrix); // Calculate model view matrix

        this.gl.glUniformMatrix4fv(mv_matrixID, 1, false, modelViewMatrix.get(scratchBuffer));
        this.gl.glProgramUniform3f(renderingProgram, fragmentColor, Roof_Color[0], Roof_Color[1], Roof_Color[2]);
        this.gl.glDrawArrays(GL_TRIANGLES, 0, 36); // Draw yellow pyrimid

        // Prime load blades vbo
        this.gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[2]);
        this.gl.glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
        this.gl.glEnableVertexAttribArray(0);

        // Calculate blades rotation per frame and apply
        modelMatrixBlade.rotate((float) frameRotationBlade, 0.0f, 0.0f, 1.0f);
        viewMatrix.mul(modelMatrixBlade, modelViewMatrix); // Calculate model view matrix

        this.gl.glUniformMatrix4fv(mv_matrixID, 1, false, modelViewMatrix.get(scratchBuffer));
        this.gl.glProgramUniform3f(renderingProgram, fragmentColor, Blade_Color[0], Blade_Color[1], Blade_Color[2]);
        this.gl.glDrawArrays(GL_TRIANGLES, 0, (27 * number_of_blades)); // Draw Blade(s)

        startTime = System.currentTimeMillis();
    }

    @Override
    public void dispose(GLAutoDrawable arg0) {
    }

    @Override
    public void reshape(GLAutoDrawable arg0, int arg1, int arg2, int arg3, int arg4) {
        // Reshape perspective matrix based on window resize
        aspectRatio = (float) glCanvas.getWidth() / (float) glCanvas.getHeight(); // Get new aspect ratio
        perspectiveMatrix.identity().perspective((float) Math.toRadians(60.0f), aspectRatio, 0.1f, 1000.0f); // Set new
    }

    /**
     * Add verticies to gpu through buffers 0 (cube) and 2 (pyrimid)
     */
    private void setupVertices() {
        // Walls north south
        float[] walls = {
                -1.0f, 1.0f, -1.0f, -1.0f, -1.0f, -1.0f, 1.0f, -1.0f, -1.0f,
                1.0f, -1.0f, -1.0f, 1.0f, 1.0f, -1.0f, -1.0f, 1.0f, -1.0f,
                1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
                -1.0f, -1.0f, 1.0f, -1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
        };

        // Pyramid verticies
        float[] pyramidPositions = {
                -1.0f, -1.0f, 1.0f, 1.0f, -1.0f, 1.0f, 0.0f, 1.0f, 0.0f, // front
                1.0f, -1.0f, 1.0f, 1.0f, -1.0f, -1.0f, 0.0f, 1.0f, 0.0f, // right
                1.0f, -1.0f, -1.0f, -1.0f, -1.0f, -1.0f, 0.0f, 1.0f, 0.0f, // back
                -1.0f, -1.0f, -1.0f, -1.0f, -1.0f, 1.0f, 0.0f, 1.0f, 0.0f, // left
        };

        this.gl.glGenVertexArrays(1, vao, 0); // make VAO
        this.gl.glBindVertexArray(vao[0]); // activate 1st VAO
        this.gl.glGenBuffers(4, vbo, 0); // make 4 VBOs

        this.gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[0]);
        FloatBuffer wallsBuffer = Buffers.newDirectFloatBuffer(walls);
        this.gl.glBufferData(GL_ARRAY_BUFFER, wallsBuffer.limit() * 4, wallsBuffer, GL_STATIC_DRAW);

        this.gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[1]);
        FloatBuffer pyrimidBuffer = Buffers.newDirectFloatBuffer(pyramidPositions);
        this.gl.glBufferData(GL_ARRAY_BUFFER, pyrimidBuffer.limit() * 4, pyrimidBuffer, GL_STATIC_DRAW);

        float[] mulitblade = new float[(27 * number_of_blades)]; // Vertices for all blades
        float bladeAngle = ((float) (Math.PI * 2) / number_of_blades); // Angle each blade should be apart

        // Vectors that make up the shape of a blade
        Matrix3f tri1 = new Matrix3f(-1.0f, 0.0f, 0.0f, -2.0f, 1.0f, 0.0f, 0.0f, 2.0f, 0.0f);
        Matrix3f tri2 = new Matrix3f(-1.0f, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 1.0f, 0.0f, 0.0f);
        Matrix3f tri3 = new Matrix3f(1.0f, 0.0f, 0.0f, 2.0f, 1.0f, 0.0f, 0.0f, 2.0f, 0.0f);

        // Scale each vector the to correct size
        tri1.scaleLocal(0.09f, 1.2f, 0.0f);
        tri2.scaleLocal(0.09f, 1.2f, 0.0f);
        tri3.scaleLocal(0.09f, 1.2f, 0.0f);

        // Create a model featuring the blades needed at an angle offset as needed
        Matrix3f rotationMatrix = new Matrix3f().rotateZ(bladeAngle);
        for (int a = 0; a < number_of_blades; a++) {
            tri1.mulLocal(rotationMatrix).get(mulitblade, (a * 27));
            tri2.mulLocal(rotationMatrix).get(mulitblade, (a * 27 + 9));
            tri3.mulLocal(rotationMatrix).get(mulitblade, (a * 27 + 18));
        }

        this.gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[2]);
        FloatBuffer bladeBuffer = Buffers.newDirectFloatBuffer(mulitblade);
        this.gl.glBufferData(GL_ARRAY_BUFFER, bladeBuffer.limit() * 4, bladeBuffer, GL_STATIC_DRAW);
    };
}