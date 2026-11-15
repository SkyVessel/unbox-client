package dev.unbox.client.input;
public record KeyEvent(int key,int scancode,int modifiers) {
 public boolean isConfirmation(){return key==257||key==335||key==32;}
 public boolean hasShiftDown(){return (modifiers&1)!=0;}
}
