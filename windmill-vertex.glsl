#version 430	// version 4.30

layout (location=0) in vec3 position;  // input is a triple
uniform mat4 mv_matrix;	// access to MV matrix
uniform mat4 p_matrix;	// access to P matrix
uniform vec3 incolor;

out vec4 vColor;

void main(void) {	// output a quadruple
    gl_Position = p_matrix * mv_matrix * vec4(position, 1.0);
    vColor = vec4(incolor, 1.0f);
}