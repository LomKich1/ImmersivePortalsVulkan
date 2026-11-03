#version 330

// 1.21.5+: the vertex shader is vanilla's screenquad.vsh (a full-screen triangle); the sampler is named
// InSampler like vanilla's blit_screen.fsh

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    // change alpha to 1.0
    fragColor = vec4(texture(InSampler, texCoord).xyz, 1.0f);
}
