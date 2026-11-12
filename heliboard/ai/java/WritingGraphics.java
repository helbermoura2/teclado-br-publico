// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;
import android.graphics.*;
import android.graphics.drawable.Drawable;
final class WritingGraphics {
    static Drawable background() {
        return new Atmosphere(false);
    }
    static Drawable background(boolean oneUiTheme) {
        return new Atmosphere(oneUiTheme);
    }
    static Drawable icon(String name) {
        return new Symbol(name);
    }
    private static final class Atmosphere extends Drawable {
        private final Paint p=new Paint(3);
        private final boolean oneUiTheme;
        Atmosphere(boolean oneUiTheme) {
            this.oneUiTheme=oneUiTheme;
        }
        @Override public void draw(Canvas canvas) {
            Rect r=getBounds();
            p.setShader(new LinearGradient(0,0,r.width(),r.height(),
                oneUiTheme ? new int[]{0xff17181d,0xff22242b,0xff1b1d25}
                           : new int[]{0xff81a8c7,0xff98c1b7,0xffb7c8c4},
                null,Shader.TileMode.CLAMP));
            canvas.drawRect(r,p);
            p.setShader(new RadialGradient(0,r.height(),Math.max(1,r.width()),
                oneUiTheme ? new int[]{0x275c7bd3,0x00232842}
                           : new int[]{0xaaad8ed1,0x008f9bca},
                null,Shader.TileMode.CLAMP));
            canvas.drawRect(r,p);
            p.setShader(null);
        }@Override public void setAlpha(int a) {
        }@Override public void setColorFilter(ColorFilter f) {
        }@Override public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }
    private static final class Symbol extends Drawable {
        private final String name;
        private final Paint p=new Paint(3);
        Symbol(String n) {
            name=n;
        }@Override public void draw(Canvas real) {
            Canvas v=real;
            v.save();
            v.translate(getBounds().left,getBounds().top);
            v.scale(getBounds().width()/24f,getBounds().height()/24f);
            p.setColor(Color.WHITE);
            p.setStrokeWidth(1.6f);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            if(name.equals("keyboard")) {
                v.drawRoundRect(2,4,22,20,3,3,p);
                for(int y=8;y<=12;y+=4)for(int x=6;x<=18;x+=4)v.drawPoint(x,y,p);
                v.drawLine(7,16,17,16,p);
            }
            else if(name.equals("info")) {
                v.drawCircle(12,12,9,p);
                v.drawLine(12,10,12,17,p);
                v.drawPoint(12,6,p);
            }
            else if(name.equals("settings")) {
                v.drawCircle(12,12,7,p);
                v.drawCircle(12,12,2.6f,p);
                for(int i=0;i<8;i++) {
                    v.save();
                    v.rotate(i*45,12,12);
                    v.drawLine(12,2,12,5,p);
                    v.restore();
                }
            }
            else if(name.equals("spelling")) {
                p.setStyle(Paint.Style.FILL);
                p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
                p.setTextSize(12);
                v.drawText("abc",2,12,p);
                p.setStyle(Paint.Style.STROKE);
                v.drawLine(3,16,13,16,p);
                v.drawLine(13,18,16,21,p);
                v.drawLine(16,21,22,14,p);
            }
            else if(name.equals("table")) {
                v.drawRoundRect(2,4,22,20,2,2,p);
                v.drawLine(2,9,22,9,p);
                v.drawLine(2,14,22,14,p);
                v.drawLine(9,4,9,20,p);
            }
            else if(name.equals("bullets")) {
                for(int y=5;y<22;y+=7) {
                    v.drawPoint(3,y,p);
                    v.drawLine(8,y,22,y,p);
                }
            }
            else if(name.equals("compose")) {
                v.drawRoundRect(3,3,21,21,3,3,p);
                v.drawLine(7,16,15,8,p);
                v.drawLine(15,8,18,11,p);
                v.drawLine(18,11,10,19,p);
            }
            else {
                v.drawRoundRect(5,3,20,21,2,2,p);
                v.drawLine(9,8,16,8,p);
                v.drawLine(9,12,16,12,p);
                v.drawLine(9,16,14,16,p);
                if(name.equals("style")) {
                    v.drawLine(1,6,8,6,p);
                    v.drawLine(6,4,8,6,p);
                }
            }
            v.restore();
        }@Override public void setAlpha(int a) {
            p.setAlpha(a);
        }@Override public void setColorFilter(ColorFilter f) {
            p.setColorFilter(f);
        }@Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}

