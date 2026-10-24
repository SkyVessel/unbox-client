#version 100
precision mediump float;
/** @resolution */
uniform vec2 u_resolution;
/** @time */
uniform float u_time;
/** @label Line color @color @default #86aaff */
uniform vec3 u_color;
/** @label Motion speed @default 0.018 */
uniform float u_speed;
float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1,311.7)))*43758.5453); }
float noise(vec2 p) {
  vec2 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f);
  return mix(mix(hash(i),hash(i+vec2(1.,0.)),f.x),mix(hash(i+vec2(0.,1.)),hash(i+vec2(1.,1.)),f.x),f.y);
}
void main() {
  vec2 uv=gl_FragCoord.xy/u_resolution;
  vec2 p=uv*vec2(3.1,2.5);
  float t=u_time*u_speed;
  float field=noise(p+vec2(t,t*.35))*.68+noise(p*1.9-vec2(t*.3,0.))*.32;
  float d=abs(fract(field*15.)-.5);
  float line=1.-smoothstep(.015,.045,d);
  float edge=smoothstep(.05,.5,uv.x);
  gl_FragColor=vec4(u_color,line*.21*edge);
}
