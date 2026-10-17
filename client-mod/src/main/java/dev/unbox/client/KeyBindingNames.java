package dev.unbox.client;

/** Legacy config numbers are GLFW codes, even when read by an SDL-based game. */
final class KeyBindingNames {
 static String migrate(String value,String actual,String defaultKey){
  if(value==null||!value.matches("-?\\d+"))return value;
  // Options stores stable names and also preserves mouse/scancode bindings.
  if(!actual.equals(defaultKey))return actual;
  int code;try{code=Integer.parseInt(value);}catch(NumberFormatException e){return null;}
  if(code==-1)return "key.keyboard.unknown";
  if(code>=65&&code<=90)return "key.keyboard."+(char)(code+32);
  if(code>=48&&code<=57)return "key.keyboard."+(char)code;
  if(code>=290&&code<=314)return "key.keyboard.f"+(code-289);
  if(code>=320&&code<=329)return "key.keyboard.keypad."+(code-320);
  String name=switch(code){
   case 32->"space";case 39->"apostrophe";case 44->"comma";case 45->"minus";case 46->"period";case 47->"slash";
   case 59->"semicolon";case 61->"equal";case 91->"left.bracket";case 92->"backslash";case 93->"right.bracket";case 96->"grave.accent";
   case 161->"world.1";case 162->"world.2";case 256->"escape";case 257->"enter";case 258->"tab";case 259->"backspace";case 260->"insert";case 261->"delete";
   case 262->"right";case 263->"left";case 264->"down";case 265->"up";case 266->"page.up";case 267->"page.down";case 268->"home";case 269->"end";
   case 280->"caps.lock";case 281->"scroll.lock";case 282->"num.lock";case 283->"print.screen";case 284->"pause";
   case 330->"keypad.decimal";case 331->"keypad.divide";case 332->"keypad.multiply";case 333->"keypad.subtract";case 334->"keypad.add";case 335->"keypad.enter";case 336->"keypad.equal";
   case 340->"left.shift";case 341->"left.control";case 342->"left.alt";case 343->"left.win";case 344->"right.shift";case 345->"right.control";case 346->"right.alt";case 347->"right.win";case 348->"menu";
   default->null;
  };return name==null?null:"key.keyboard."+name;
 }
}
