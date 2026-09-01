package com.example.dailylist;

import android.app.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainActivity extends Activity {
    static final int INK = Color.rgb(22,42,58), MUTED = Color.rgb(103,124,142), BLUE = Color.rgb(35,88,140);
    static final int PALE_BLUE = Color.rgb(228,238,247), BACKGROUND = Color.rgb(243,247,251), DIVIDER = Color.rgb(211,223,233);
    static final int DELETE = Color.rgb(183,55,55);
    final ArrayList<Task> tasks = new ArrayList<>();
    final Handler handler = new Handler(Looper.getMainLooper());
    LinearLayout root, list, undoBar; TextView title, subtitle; Button todayButton, tomorrowButton, addButton; LocalDate shown;
    Task pendingDeletedTask; int pendingDeletedIndex = -1; Runnable dismissUndo;
    EditText activeEditor; Task activeEditTask;
    android.content.SharedPreferences prefs;

    static class Task {
        long id, source; String text, date; boolean done;
        Task(long i,String t,String d,boolean x,long s){id=i;text=t;date=d;done=x;source=s;}
    }

    class SwipeTaskRow extends LinearLayout {
        final Task task; final int touchSlop; float downX, downY; boolean swiping;
        SwipeTaskRow(Task t){
            super(MainActivity.this);task=t;touchSlop=ViewConfiguration.get(MainActivity.this).getScaledTouchSlop();
            setOrientation(LinearLayout.HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setPadding(dp(6),dp(5),0,dp(5));setBackgroundColor(BACKGROUND);setClickable(true);
        }
        @Override public boolean onInterceptTouchEvent(MotionEvent event){
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN){downX=event.getX();downY=event.getY();swiping=false;animate().cancel();return false;}
            if(event.getActionMasked()==MotionEvent.ACTION_MOVE){
                float dx=event.getX()-downX,dy=event.getY()-downY;
                if(dx < -touchSlop && Math.abs(dx) > Math.abs(dy)*1.2f){swiping=true;getParent().requestDisallowInterceptTouchEvent(true);return true;}
            }
            return false;
        }
        @Override public boolean onTouchEvent(MotionEvent event){
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    downX=event.getX();downY=event.getY();swiping=false;return true;
                case MotionEvent.ACTION_MOVE:
                    float dx=event.getX()-downX,dy=event.getY()-downY;
                    if(!swiping && dx < -touchSlop && Math.abs(dx) > Math.abs(dy)*1.2f){swiping=true;getParent().requestDisallowInterceptTouchEvent(true);}
                    if(swiping){float offset=Math.min(0,dx);setTranslationX(offset);setAlpha(Math.max(.55f,1f-Math.abs(offset)/Math.max(1f,getWidth()*1.5f)));}
                    return true;
                case MotionEvent.ACTION_UP:
                    getParent().requestDisallowInterceptTouchEvent(false);
                    if(swiping && -getTranslationX() >= Math.max(dp(72),getWidth()*.25f)){
                        animate().translationX(-getWidth()).alpha(0f).setDuration(160).withEndAction(()->deleteTaskWithUndo(task)).start();
                    }else resetPosition();
                    swiping=false;return true;
                case MotionEvent.ACTION_CANCEL:
                    getParent().requestDisallowInterceptTouchEvent(false);resetPosition();swiping=false;return true;
            }
            return super.onTouchEvent(event);
        }
        void resetPosition(){animate().translationX(0).alpha(1f).setDuration(160).start();}
    }

    @Override public void onCreate(Bundle b){ super.onCreate(b); prefs=getSharedPreferences("daily",MODE_PRIVATE); load(); shown=LocalDate.now(); build(); render(); }
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN&&activeEditor!=null){Rect bounds=new Rect();activeEditor.getGlobalVisibleRect(bounds);if(!bounds.contains((int)event.getRawX(),(int)event.getRawY()))finishInlineEdit();}
        return super.dispatchTouchEvent(event);
    }
    int dp(int n){ return (int)(n*getResources().getDisplayMetrics().density+.5f); }
    TextView text(String s,int sp,int color){ TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);return v; }
    GradientDrawable bg(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    void build(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(22),dp(18),dp(22),dp(18));root.setBackgroundColor(BACKGROUND);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            view.setPadding(dp(22)+insets.getSystemWindowInsetLeft(),dp(18)+insets.getSystemWindowInsetTop(),dp(22)+insets.getSystemWindowInsetRight(),dp(18)+insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER_VERTICAL);
        Button back=new Button(this);back.setText("‹");back.setTextSize(25);back.setTextColor(BLUE);back.setBackground(bg(PALE_BLUE,14));back.setOnClickListener(v->{shown=shown.minusDays(1);render();});
        LinearLayout heads=new LinearLayout(this);heads.setOrientation(LinearLayout.VERTICAL);heads.setGravity(Gravity.CENTER);heads.setPadding(dp(10),0,dp(10),0);
        title=text("",27,INK);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);title.setGravity(Gravity.CENTER);subtitle=text("",14,MUTED);subtitle.setGravity(Gravity.CENTER);heads.addView(title);heads.addView(subtitle);
        Button forward=new Button(this);forward.setText("›");forward.setTextSize(25);forward.setTextColor(BLUE);forward.setBackground(bg(PALE_BLUE,14));forward.setOnClickListener(v->{shown=shown.plusDays(1);render();});
        nav.addView(back,new LinearLayout.LayoutParams(dp(50),dp(50)));nav.addView(heads,new LinearLayout.LayoutParams(0,dp(66),1));nav.addView(forward,new LinearLayout.LayoutParams(dp(50),dp(50)));root.addView(nav);
        LinearLayout quick=new LinearLayout(this);quick.setGravity(Gravity.CENTER);quick.setPadding(0,dp(6),0,dp(12));
        todayButton=new Button(this);todayButton.setText("Today");todayButton.setOnClickListener(v->{shown=LocalDate.now();render();});tomorrowButton=new Button(this);tomorrowButton.setText("Tomorrow");tomorrowButton.setOnClickListener(v->{shown=LocalDate.now().plusDays(1);render();});quick.addView(todayButton);quick.addView(tomorrowButton);root.addView(quick);
        ScrollView scroll=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        addButton=new Button(this);addButton.setText("＋  Add a task");addButton.setTextSize(17);addButton.setTextColor(Color.WHITE);addButton.setBackground(bg(BLUE,16));addButton.setOnClickListener(v->addDialog());root.addView(addButton,new LinearLayout.LayoutParams(-1,dp(58)));setContentView(root);
    }
    void render(){
        LocalDate now=LocalDate.now();title.setText(shown.equals(now)?"Today":shown.equals(now.plusDays(1))?"Tomorrow":shown.format(DateTimeFormatter.ofPattern("EEEE")));
        subtitle.setText(shown.format(DateTimeFormatter.ofPattern("d MMMM yyyy")));list.removeAllViews();
        styleDayButton(todayButton,shown.equals(now));styleDayButton(tomorrowButton,shown.equals(now.plusDays(1)));
        boolean any=false;for(Task t:tasks)if(t.date.equals(shown.toString())){addTaskRow(t);any=true;}
        if(shown.equals(now)){
            ArrayList<Task> old=new ArrayList<>();for(Task t:tasks)if(!t.done&&LocalDate.parse(t.date).isBefore(now)&&!carried(t.id,now))old.add(t);
            if(!old.isEmpty()){ TextView h=text("FROM PREVIOUS DAYS",12,MUTED);h.setTypeface(Typeface.DEFAULT,Typeface.BOLD);h.setPadding(dp(4),dp(26),0,dp(8));list.addView(h);for(Task t:old)addCarryRow(t);any=true; }
        }
        if(!any){TextView empty=text("Nothing planned yet.\nEnjoy the space — or add a task below.",16,MUTED);empty.setGravity(Gravity.CENTER);empty.setPadding(0,dp(80),0,0);list.addView(empty,new LinearLayout.LayoutParams(-1,dp(180)));}
    }
    void styleDayButton(Button button,boolean selected){button.setTextColor(selected?Color.WHITE:BLUE);button.setBackground(bg(selected?BLUE:PALE_BLUE,14));}
    boolean carried(long source,LocalDate day){for(Task t:tasks)if(t.source==source&&t.date.equals(day.toString()))return true;return false;}
    void addTaskRow(Task t){
        FrameLayout swipeLayer=new FrameLayout(this);TextView deleteHint=text("Delete",14,Color.WHITE);deleteHint.setGravity(Gravity.CENTER);deleteHint.setTypeface(Typeface.DEFAULT,Typeface.BOLD);deleteHint.setBackgroundColor(DELETE);
        FrameLayout.LayoutParams deleteParams=new FrameLayout.LayoutParams(dp(92),-1,Gravity.END);swipeLayer.addView(deleteHint,deleteParams);
        SwipeTaskRow row=new SwipeTaskRow(t);
        CheckBox cb=new CheckBox(this);cb.setChecked(t.done);cb.setButtonTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{BLUE,MUTED}));
        EditText label=new EditText(this);label.setText(t.text);label.setTextSize(17);label.setTextColor(t.done?MUTED:INK);label.setGravity(Gravity.CENTER_VERTICAL);label.setSingleLine(true);label.setImeOptions(EditorInfo.IME_ACTION_DONE);label.setPadding(dp(6),0,dp(4),0);label.setBackgroundColor(Color.TRANSPARENT);label.setFocusable(false);label.setCursorVisible(false);if(t.done)label.setPaintFlags(label.getPaintFlags()|android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        cb.setOnCheckedChangeListener((b,x)->{t.done=x;save();render();});label.setOnClickListener(v->beginInlineEdit(label,t));label.setOnLongClickListener(v->{if(activeEditor==label)return false;deleteDialog(t);return true;});label.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_DONE){finishInlineEdit();return true;}return false;});label.setOnFocusChangeListener((v,focused)->{if(!focused&&activeEditor==label)finishInlineEdit();});row.addView(cb,new LinearLayout.LayoutParams(dp(48),dp(52)));row.addView(label,new LinearLayout.LayoutParams(0,dp(52),1));swipeLayer.addView(row,new FrameLayout.LayoutParams(-1,-1));list.addView(swipeLayer,new LinearLayout.LayoutParams(-1,dp(62)));
        View line=new View(this);line.setBackgroundColor(DIVIDER);list.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
    }
    void addCarryRow(Task old){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),0,0,0);TextView plus=text("＋",23,BLUE);TextView label=text(old.text,16,Color.rgb(137,153,168));label.setPadding(dp(8),0,0,0);row.addView(plus,new LinearLayout.LayoutParams(dp(42),dp(52)));row.addView(label,new LinearLayout.LayoutParams(0,dp(52),1));row.setOnClickListener(v->{tasks.add(new Task(System.currentTimeMillis(),old.text,LocalDate.now().toString(),false,old.id));save();render();});list.addView(row);
    }
    void addDialog(){
        LinearLayout box=new LinearLayout(this);box.setPadding(dp(24),dp(8),dp(24),0);box.setOrientation(LinearLayout.VERTICAL);EditText input=new EditText(this);input.setHint("What needs doing?");input.setSingleLine(true);box.addView(input);RadioGroup group=new RadioGroup(this);group.setOrientation(RadioGroup.HORIZONTAL);RadioButton cur=new RadioButton(this);cur.setText(shown.equals(LocalDate.now().plusDays(1))?"Tomorrow":shown.equals(LocalDate.now())?"Today":shown.format(DateTimeFormatter.ofPattern("EEE d MMM")));cur.setId(1);cur.setChecked(true);RadioButton next=new RadioButton(this);next.setText("Next day");next.setId(2);group.addView(cur);group.addView(next);box.addView(group);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("New task").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Add",null).create();d.setOnShowListener(x->{d.getButton(-1).setOnClickListener(v->{String s=input.getText().toString().trim();if(s.isEmpty()){input.setError("Enter a task");return;}LocalDate date=group.getCheckedRadioButtonId()==2?shown.plusDays(1):shown;tasks.add(new Task(System.currentTimeMillis(),s,date.toString(),false,0));save();d.dismiss();render();});input.requestFocus();d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);});d.show();
    }
    void beginInlineEdit(EditText editor,Task task){
        if(activeEditor==editor)return;finishInlineEdit();activeEditor=editor;activeEditTask=task;editor.setFocusableInTouchMode(true);editor.setFocusable(true);editor.setCursorVisible(true);editor.setBackground(bg(PALE_BLUE,8));editor.requestFocus();editor.setSelection(editor.getText().length());handler.post(()->{if(activeEditor==editor)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(editor,InputMethodManager.SHOW_IMPLICIT);});
    }
    void finishInlineEdit(){
        if(activeEditor==null)return;EditText editor=activeEditor;Task task=activeEditTask;String updated=editor.getText().toString().trim();activeEditor=null;activeEditTask=null;if(updated.isEmpty())editor.setText(task.text);else if(!updated.equals(task.text)){task.text=updated;save();}editor.setCursorVisible(false);editor.setBackgroundColor(Color.TRANSPARENT);editor.setFocusable(false);editor.clearFocus();((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(editor.getWindowToken(),0);
    }
    void deleteDialog(Task t){new AlertDialog.Builder(this).setTitle("Delete task?").setMessage(t.text).setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->deleteTaskWithUndo(t)).show();}
    void deleteTaskWithUndo(Task t){if(activeEditTask==t)finishInlineEdit();int index=tasks.indexOf(t);if(index<0)return;tasks.remove(index);save();render();showUndo(t,index);}
    void showUndo(Task task,int index){
        if(dismissUndo!=null)handler.removeCallbacks(dismissUndo);pendingDeletedTask=task;pendingDeletedIndex=index;
        if(undoBar==null){
            undoBar=new LinearLayout(this);undoBar.setGravity(Gravity.CENTER_VERTICAL);undoBar.setPadding(dp(18),0,dp(10),0);undoBar.setBackground(bg(INK,14));TextView message=text("Task deleted",15,Color.WHITE);TextView undo=text("UNDO",14,PALE_BLUE);undo.setTypeface(Typeface.DEFAULT,Typeface.BOLD);undo.setGravity(Gravity.CENTER);undo.setPadding(dp(18),0,dp(8),0);undo.setClickable(true);undo.setFocusable(true);undo.setOnClickListener(v->undoDelete());undoBar.addView(message,new LinearLayout.LayoutParams(0,-1,1));undoBar.addView(undo,new LinearLayout.LayoutParams(dp(84),-1));
        }
        if(undoBar.getParent()==null){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.setMargins(0,dp(8),0,dp(8));root.addView(undoBar,root.indexOfChild(addButton),p);undoBar.setAlpha(0f);undoBar.setTranslationY(dp(10));undoBar.animate().alpha(1f).translationY(0).setDuration(140).start();}
        dismissUndo=()->{pendingDeletedTask=null;pendingDeletedIndex=-1;hideUndo();};handler.postDelayed(dismissUndo,4500);
    }
    void undoDelete(){
        if(pendingDeletedTask==null)return;if(dismissUndo!=null)handler.removeCallbacks(dismissUndo);int index=Math.max(0,Math.min(pendingDeletedIndex,tasks.size()));tasks.add(index,pendingDeletedTask);pendingDeletedTask=null;pendingDeletedIndex=-1;save();render();hideUndo();
    }
    void hideUndo(){if(undoBar!=null&&undoBar.getParent()!=null){undoBar.animate().cancel();root.removeView(undoBar);}}
    @Override protected void onPause(){finishInlineEdit();super.onPause();}
    @Override protected void onDestroy(){if(dismissUndo!=null)handler.removeCallbacks(dismissUndo);super.onDestroy();}
    void load(){try{JSONArray a=new JSONArray(prefs.getString("tasks","[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);tasks.add(new Task(o.getLong("id"),o.getString("text"),o.getString("date"),o.optBoolean("done"),o.optLong("source")));}}catch(Exception ignored){}}
    void save(){JSONArray a=new JSONArray();try{for(Task t:tasks){JSONObject o=new JSONObject();o.put("id",t.id);o.put("text",t.text);o.put("date",t.date);o.put("done",t.done);o.put("source",t.source);a.put(o);}}catch(Exception ignored){}prefs.edit().putString("tasks",a.toString()).apply();}
}
