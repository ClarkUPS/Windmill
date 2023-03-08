#version 430

in vec4 vColor;	// same name as above
out vec4 color; // Output final color

void main(void)
{
    color = vColor; // Same color input as output
}