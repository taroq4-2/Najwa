package org.najwa.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.ui.text.style.TextAlign

data class Palette(val bg: Color,val surface: Color,val text: Color,val secondary: Color,val border: Color,val green: Color,val mint: Color,val amber: Color,val selected: Color,val track: Color,val dark: Boolean=false)
// Madar Al-Sakeenah: ink, jade and dawn-gold. Text/secondary tones are contrast-led.
val Dark=Palette(Color(0xFF071715),Color(0xFF112521),Color(0xFFF6F4EC),Color(0xFFA9C0B6),Color(0xFF2D4840),Color(0xFF3EB28C),Color(0xFFA5E8C4),Color(0xFFF0C787),Color(0xFF1B3B32),Color(0xFF29483D),true)
val Light=Palette(Color(0xFFF5F3EB),Color(0xFFFEFDF7),Color(0xFF19352E),Color(0xFF58756A),Color(0xFFD8E0D4),Color(0xFF15795C),Color(0xFF126D53),Color(0xFF81551E),Color(0xFFE6F1E8),Color(0xFFDCE7DE))
val LocalPalette=staticCompositionLocalOf { Dark }
@Composable fun Panel(modifier: Modifier=Modifier, radius: Int=28, selected: Boolean=false, content: @Composable ColumnScope.()->Unit) {
    val c=LocalPalette.current; val shape=RoundedCornerShape(radius.dp)
    Column(modifier.clip(shape).background(if(selected) c.selected else c.surface,shape).border(1.dp,if(selected)c.green else c.border,shape),content=content)
}
@Composable fun Action(label: String,modifier: Modifier=Modifier,primary: Boolean=false,onClick: ()->Unit) {
    val c=LocalPalette.current
    if(primary) Button(onClick=onClick,modifier=modifier.heightIn(min=50.dp),shape=RoundedCornerShape(18.dp)) {
        Text(uiText(label),fontSize=15.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
    } else OutlinedButton(onClick=onClick,modifier=modifier.heightIn(min=50.dp),shape=RoundedCornerShape(16.dp),border=androidx.compose.foundation.BorderStroke(1.dp,c.border)) {
        Text(uiText(label),color=c.text,fontSize=14.sp,fontWeight=FontWeight.Medium,textAlign=TextAlign.Center)
    }
}

/** [dynamic]: optional Material You accent from the wallpaper (Android 12+); the rest of the identity stays. */
@Composable fun NajwaTheme(dark: Boolean,dynamic: Boolean=false,content: @Composable ()->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val base0=if(dark)Dark else Light
    val c=if(dynamic && android.os.Build.VERSION.SDK_INT>=31) {
        val system=if(dark)dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        base0.copy(green=system.primary,mint=system.primary,selected=system.primaryContainer.copy(alpha=if(dark)1f else .7f))
    } else base0
    val base=if(dark)darkColorScheme() else lightColorScheme()
    val scheme=base.copy(primary=if(dark)c.green else c.mint,onPrimary=if(dark)Color(0xFF071715) else Color.White,
        primaryContainer=c.selected,onPrimaryContainer=c.mint,secondary=c.mint,onSecondary=c.bg,
        secondaryContainer=c.selected,onSecondaryContainer=c.text,background=c.bg,onBackground=c.text,
        surface=c.surface,onSurface=c.text,surfaceVariant=c.selected,onSurfaceVariant=c.secondary,
        surfaceContainerHigh=c.surface,surfaceContainer=c.surface,surfaceContainerLow=c.surface,
        outline=c.border,outlineVariant=c.border,error=c.amber)
    val locale=androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val direction=if(android.text.TextUtils.getLayoutDirectionFromLocale(locale)==android.view.View.LAYOUT_DIRECTION_RTL)
        androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
    CompositionLocalProvider(LocalPalette provides c,androidx.compose.ui.platform.LocalLayoutDirection provides direction) {
        MaterialTheme(colorScheme=scheme,typography=najwaTypography(),content=content)
    }
}
/** A soft colour family for each settings section and adhkar category, readable in both themes. */
data class Accent(val tint: Color,val ink: Color)
@Composable fun accent(name: String): Accent {
    val c=LocalPalette.current
    val d=c.dark
    return when(name) {
        "teal"->if(d) Accent(Color(0xFF123A33),Color(0xFF5DCAA5)) else Accent(Color(0xFFE1F5EE),Color(0xFF0F6E56))
        "amber"->if(d) Accent(Color(0xFF3A2F14),Color(0xFFF0B85A)) else Accent(Color(0xFFFAEEDA),Color(0xFF854F0B))
        "blue"->if(d) Accent(Color(0xFF1C2A45),Color(0xFF8DBCEE)) else Accent(Color(0xFFE6F1FB),Color(0xFF185FA5))
        "violet"->if(d) Accent(Color(0xFF2A2546),Color(0xFFB4AEF0)) else Accent(Color(0xFFEEEDFE),Color(0xFF534AB7))
        "pink"->if(d) Accent(Color(0xFF3A2230),Color(0xFFEE9DB9)) else Accent(Color(0xFFFBEAF0),Color(0xFF993556))
        "coral"->if(d) Accent(Color(0xFF3A2219),Color(0xFFF2A588)) else Accent(Color(0xFFFAECE7),Color(0xFF993C1D))
        else->Accent(c.selected,c.mint)
    }
}
/** A rounded tinted square holding a glyph. */
@Composable fun IconBadge(glyph: String,a: Accent,size: Int=42,modifier: Modifier=Modifier) {
    Box(modifier.size(size.dp).background(a.tint,RoundedCornerShape((size/3).dp)),contentAlignment=Alignment.Center) {Glyph(glyph,a.ink,Modifier.size((size*0.5f).dp))}
}

@Composable fun Mark(modifier: Modifier=Modifier) {
    androidx.compose.foundation.Image(
        androidx.compose.ui.res.painterResource(org.najwa.app.R.drawable.ic_najwa_mark), null,
        modifier.size(36.dp), colorFilter=androidx.compose.ui.graphics.ColorFilter.tint(LocalPalette.current.mint)
    )
}
@Composable fun Glyph(name: String,color: Color=LocalPalette.current.secondary,modifier: Modifier=Modifier) {
    val rtl=androidx.compose.ui.platform.LocalLayoutDirection.current==androidx.compose.ui.unit.LayoutDirection.Rtl
    Canvas(modifier.size(22.dp)) {
        val unit=size.width/24; val stroke=1.8f*unit
        fun line(x: Float,y: Float,x2: Float,y2: Float) = drawLine(color,Offset(x*unit,y*unit),Offset(x2*unit,y2*unit),stroke,StrokeCap.Round)
        fun path(vararg points: Pair<Float,Float>) { val p=Path(); points.forEachIndexed { i,v -> if(i==0)p.moveTo(v.first*unit,v.second*unit) else p.lineTo(v.first*unit,v.second*unit) }; drawPath(p,color,style=Stroke(stroke,cap=StrokeCap.Round)) }
        when(name) {
            "home"->{path(3f to 11f,12f to 3f,21f to 11f); path(6f to 10f,6f to 21f,18f to 21f,18f to 10f)}
            "sound"->{path(3f to 9f,7f to 9f,12f to 5f,12f to 19f,7f to 15f,3f to 15f,3f to 9f); line(17f,9f,21f,15f);line(21f,9f,17f,15f)}
            "check"->path(5f to 12f,10f to 17f,20f to 7f)
            "back","chevron"->{
                val pointsRight=if(name=="back")rtl else !rtl
                val outer=if(pointsRight)9f else 15f
                val tip=24f-outer
                path(outer to 6f,tip to 12f,outer to 18f)
            }
            "down"->path(6f to 9f,12f to 15f,18f to 9f)
            "dot"->drawCircle(color,4.5f*unit)
            "widget"->{path(3f to 3f,10f to 3f,10f to 10f,3f to 10f,3f to 3f);path(14f to 3f,21f to 3f,21f to 10f,14f to 10f,14f to 3f);path(3f to 14f,10f to 14f,10f to 21f,3f to 21f,3f to 14f);path(14f to 14f,21f to 14f,21f to 21f,14f to 21f,14f to 14f)}
            "bell"->{path(5f to 17f,7f to 14f,7f to 8f,9f to 5f,15f to 5f,17f to 8f,17f to 14f,19f to 17f,5f to 17f);drawArc(color,0f,180f,false,Offset(10*unit,18*unit),Size(4*unit,3*unit),style=Stroke(stroke))}
            "palette"->{drawCircle(color,9*unit,style=Stroke(stroke));drawCircle(color,2*unit,Offset(9*unit,8*unit));drawCircle(color,2*unit,Offset(16*unit,10*unit));drawCircle(color,2*unit,Offset(10*unit,16*unit))}
            "settings"->{drawCircle(color,6.2f*unit,Offset(12*unit,12*unit),style=Stroke(stroke));drawCircle(color,2.4f*unit,Offset(12*unit,12*unit),style=Stroke(stroke))
                repeat(8) {i->val a=i*Math.PI/4+Math.PI/8;line((12+6.2*kotlin.math.cos(a)).toFloat(),(12+6.2*kotlin.math.sin(a)).toFloat(),(12+9.4*kotlin.math.cos(a)).toFloat(),(12+9.4*kotlin.math.sin(a)).toFloat())}}
            "moon"->{val p=Path();p.moveTo(16*unit,3.5f*unit);p.cubicTo(8*unit,3*unit,3*unit,8.5f*unit,3.5f*unit,13*unit);p.cubicTo(4*unit,18.5f*unit,10*unit,22*unit,17*unit,19.5f*unit)
                p.cubicTo(11.5f*unit,18*unit,8.5f*unit,14*unit,9.5f*unit,9.5f*unit);p.cubicTo(10.3f*unit,6.5f*unit,12.8f*unit,4.3f*unit,16*unit,3.5f*unit);drawPath(p,color,style=Stroke(stroke,join=androidx.compose.ui.graphics.StrokeJoin.Round))}
            "speaker"->{path(3f to 9f,7f to 9f,12f to 5f,12f to 19f,7f to 15f,3f to 15f,3f to 9f);drawArc(color,-50f,100f,false,Offset(9*unit,7*unit),Size(10*unit,10*unit),style=Stroke(stroke,cap=StrokeCap.Round))
                drawArc(color,-55f,110f,false,Offset(7*unit,3.5f*unit),Size(17*unit,17*unit),style=Stroke(stroke,cap=StrokeCap.Round))}
            "mosque"->{drawArc(color,180f,180f,false,Offset(5*unit,6*unit),Size(12*unit,12*unit),style=Stroke(stroke));path(5f to 12f,5f to 21f,17f to 21f,17f to 12f);line(11f,3f,11f,6f)
                line(20.5f,6f,20.5f,21f);line(2f,21f,22f,21f);path(9f to 21f,9f to 16f,13f to 16f,13f to 21f)}
            "mushaf"->{path(5f to 3f,18f to 3f,18f to 21f,5f to 21f,5f to 3f);line(8f,3f,8f,21f);path(13f to 9f,15.5f to 12f,13f to 15f,10.5f to 12f,13f to 9f)}
            "data"->{drawOval(color,Offset(5*unit,3*unit),Size(14*unit,5*unit),style=Stroke(stroke));line(5f,5.5f,5f,18.5f);line(19f,5.5f,19f,18.5f)
                drawArc(color,0f,180f,false,Offset(5*unit,9.5f*unit),Size(14*unit,5*unit),style=Stroke(stroke));drawArc(color,0f,180f,false,Offset(5*unit,16*unit),Size(14*unit,5*unit),style=Stroke(stroke))}
            "undo"->{drawArc(color,200f,250f,false,Offset(5*unit,5*unit),Size(14*unit,14*unit),style=Stroke(stroke,cap=StrokeCap.Round));path(3f to 7f,5.4f to 11.2f,9.6f to 9.4f)}
            "vibrate"->{path(8f to 4f,16f to 4f,16f to 20f,8f to 20f,8f to 4f);line(4f,9f,4f,15f);line(20f,9f,20f,15f);line(1.5f,10.5f,1.5f,13.5f);line(22.5f,10.5f,22.5f,13.5f)}
            "sparkle"->{val p=Path();p.moveTo(12*unit,3*unit);p.quadraticTo(13*unit,11*unit,21*unit,12*unit);p.quadraticTo(13*unit,13*unit,12*unit,21*unit);p.quadraticTo(11*unit,13*unit,3*unit,12*unit);p.quadraticTo(11*unit,11*unit,12*unit,3*unit);drawPath(p,color,style=Stroke(stroke,join=androidx.compose.ui.graphics.StrokeJoin.Round))}
            "bed"->{line(3f,6f,3f,19f);path(3f to 15f,21f to 15f,21f to 19f);path(3f to 11f,9f to 11f,9f to 15f);path(9f to 11f,18f to 11f,21f to 13.5f,21f to 15f);drawCircle(color,1.8f*unit,Offset(6*unit,8*unit))}
            "sunrise"->{drawArc(color,180f,180f,false,Offset(7*unit,10*unit),Size(10*unit,10*unit),style=Stroke(stroke));line(2f,18f,22f,18f);line(12f,3f,12f,6.5f);line(4.5f,8f,6.5f,10f);line(19.5f,8f,17.5f,10f);line(7f,21f,17f,21f)}
            "plus"->{line(12f,5f,12f,19f);line(5f,12f,19f,12f)}
            "minus"->line(5f,12f,19f,12f)
            "sun"->{drawCircle(color,4*unit,style=Stroke(stroke)); repeat(8) { i->val a=i*Math.PI/4; line((12+7*kotlin.math.cos(a)).toFloat(),(12+7*kotlin.math.sin(a)).toFloat(),(12+10*kotlin.math.cos(a)).toFloat(),(12+10*kotlin.math.sin(a)).toFloat()) }}
            "pin"->{val p=Path();p.moveTo(12*unit,22*unit);p.cubicTo(1*unit,11*unit,5*unit,3*unit,12*unit,3*unit);p.cubicTo(19*unit,3*unit,23*unit,11*unit,12*unit,22*unit);drawPath(p,color,style=Stroke(stroke));drawCircle(color,2.5f*unit,Offset(12*unit,10*unit),style=Stroke(stroke))}
            "shield"->{path(12f to 3f,20f to 7f,19f to 15f,12f to 22f,5f to 15f,4f to 7f,12f to 3f);path(8f to 12f,11f to 15f,16f to 10f)}
            "home"->{path(3f to 11f,12f to 4f,21f to 11f);path(6f to 10f,6f to 20f,18f to 20f,18f to 10f);path(10f to 20f,10f to 14f,14f to 14f,14f to 20f)}
            "globe"->{drawCircle(color,9*unit,style=Stroke(stroke));line(3f,12f,21f,12f);drawOval(color,Offset(8*unit,3*unit),Size(8*unit,18*unit),style=Stroke(stroke))}
            "star"->path(12f to 3f,14.6f to 9f,21f to 9.5f,16f to 13.8f,17.6f to 20.5f,12f to 17f,6.4f to 20.5f,8f to 13.8f,3f to 9.5f,9.4f to 9f,12f to 3f)
            "share"->{drawCircle(color,2.6f*unit,Offset(18*unit,5*unit),style=Stroke(stroke));drawCircle(color,2.6f*unit,Offset(6*unit,12*unit),style=Stroke(stroke));drawCircle(color,2.6f*unit,Offset(18*unit,19*unit),style=Stroke(stroke));line(8.3f,10.8f,15.7f,6.2f);line(8.3f,13.2f,15.7f,17.8f)}
            "search"->{drawCircle(color,6.5f*unit,Offset(10.5f*unit,10.5f*unit),style=Stroke(stroke));line(15.5f,15.5f,21f,21f)}
            "beads"->{for(i in 0 until 9){val a=i*Math.PI*2/9;drawCircle(color,1.6f*unit,Offset((12+7*kotlin.math.cos(a)).toFloat()*unit,(11+7*kotlin.math.sin(a)).toFloat()*unit))};line(12f,18f,12f,22f)}
            "book"->{path(12f to 6f,6f to 4f,3f to 5f,3f to 19f,6f to 18f,12f to 20f,18f to 18f,21f to 19f,21f to 5f,18f to 4f,12f to 6f);line(12f,6f,12f,20f)}
            "calendar"->{path(4f to 6f,20f to 6f,20f to 20f,4f to 20f,4f to 6f);line(4f,10f,20f,10f);line(8f,3f,8f,7f);line(16f,3f,16f,7f);drawCircle(color,1.2f*unit,Offset(8*unit,14*unit));drawCircle(color,1.2f*unit,Offset(12*unit,14*unit));drawCircle(color,1.2f*unit,Offset(16*unit,14*unit))}
            "restore"->{drawArc(color,-40f,300f,false,Offset(3*unit,3*unit),Size(18*unit,18*unit),style=Stroke(stroke,cap=StrokeCap.Round));path(3f to 21f,3f to 14f,10f to 14f)}
            else->{drawCircle(color,9*unit,style=Stroke(stroke));line(12f,6f,12f,12f);line(12f,12f,16f,15f)}
        }
    }
}
/** Kaaba with a direction marker, for the qibla entry points. */
@Composable fun QiblaGlyph(color: Color,modifier: Modifier=Modifier) {
    Canvas(modifier.size(22.dp)) {
        val u=size.width/24
        drawRect(color,Offset(7*u,9*u),Size(10*u,10*u),style=Stroke(1.8f*u))
        drawLine(color,Offset(7*u,12.5f*u),Offset(17*u,12.5f*u),1.8f*u)
        val p=Path();p.moveTo(12*u,2*u);p.lineTo(15*u,6.5f*u);p.lineTo(9*u,6.5f*u);p.close()
        drawPath(p,color)
    }
}
