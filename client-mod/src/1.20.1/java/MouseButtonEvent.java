package dev.unbox.client.input;
public record MouseButtonEvent(double x,double y,MouseButtonInfo buttonInfo){public int button(){return buttonInfo.button();}}
