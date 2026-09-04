package com.example.dailylist;

import android.app.*;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.content.pm.PackageInfo;
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
    final ArrayList<Task> pendingPromotedChildren = new ArrayList<>();
    final IdentityHashMap<View,Float> dragPreviewTargets = new IdentityHashMap<>();
    EditText activeEditor; Task activeEditTask;
    android.content.SharedPreferences prefs;

    static class Task {
        long id, source, parent; String text, date; boolean done;
        Task(long i,String t,String d,boolean x,long s,long p){id=i;text=t;date=d;done=x;source=s;parent=p;}
    }

    static class DragPlacement {
        int insert; long parent;
        DragPlacement(int i,long p){insert=i;parent=p;}
    }

    class SwipeTaskRow extends LinearLayout {
        final Task task; final int touchSlop; float downX, downY; boolean swiping, dragging; Runnable beginDrag;
        SwipeTaskRow(Task t){
            super(MainActivity.this);task=t;touchSlop=ViewConfiguration.get(MainActivity.this).getScaledTouchSlop();
            setOrientation(LinearLayout.HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setPadding(dp(isSubtask(t)?34:6),dp(5),0,dp(5));setBackgroundColor(BACKGROUND);setClickable(true);
        }
        void startTracking(MotionEvent event){
            cancelDragHold();downX=event.getRawX();downY=event.getRawY();swiping=false;dragging=false;animate().cancel();
            beginDrag=()->{if(!isAttachedToWindow())return;if(activeEditTask==task)finishInlineEdit();dragging=true;performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);((View)getParent()).setElevation(dp(8));setBackground(bg(PALE_BLUE,12));getParent().requestDisallowInterceptTouchEvent(true);previewTaskDrag(task,0,0);};
            handler.postDelayed(beginDrag,ViewConfiguration.getLongPressTimeout());
        }
        void cancelDragHold(){if(beginDrag!=null){handler.removeCallbacks(beginDrag);beginDrag=null;}}
        @Override public boolean onInterceptTouchEvent(MotionEvent event){
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN){startTracking(event);return false;}
            if(event.getActionMasked()==MotionEvent.ACTION_MOVE){
                float dx=event.getRawX()-downX,dy=event.getRawY()-downY;
                if(dragging)return true;
                if(Math.abs(dx)>touchSlop||Math.abs(dy)>touchSlop){cancelDragHold();if(dx < -touchSlop && Math.abs(dx) > Math.abs(dy)*1.2f){swiping=true;getParent().requestDisallowInterceptTouchEvent(true);return true;}}
            }
            if(event.getActionMasked()==MotionEvent.ACTION_UP){cancelDragHold();if(dragging)return true;}
            if(event.getActionMasked()==MotionEvent.ACTION_CANCEL)cancelDragHold();
            return false;
        }
        @Override public boolean onTouchEvent(MotionEvent event){
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    startTracking(event);return true;
                case MotionEvent.ACTION_MOVE:
                    float dx=event.getRawX()-downX,dy=event.getRawY()-downY;
                    if(dragging){previewTaskDrag(task,dx,dy);return true;}
                    if(!swiping && dx < -touchSlop && Math.abs(dx) > Math.abs(dy)*1.2f){swiping=true;getParent().requestDisallowInterceptTouchEvent(true);}
                    if(swiping){float offset=Math.min(0,dx);setTranslationX(offset);setAlpha(Math.max(.55f,1f-Math.abs(offset)/Math.max(1f,getWidth()*1.5f)));}
                    return true;
                case MotionEvent.ACTION_UP:
                    cancelDragHold();getParent().requestDisallowInterceptTouchEvent(false);
                    if(dragging){float dragX=event.getRawX()-downX,dragY=event.getRawY()-downY;DragPlacement placement=previewTaskDrag(task,dragX,dragY);dragging=false;setBackgroundColor(BACKGROUND);clearDragPreview();finishTaskDrag(task,placement,dragX,dragY);return true;}
                    if(swiping && -getTranslationX() >= Math.max(dp(72),getWidth()*.25f)){
                        animate().translationX(-getWidth()).alpha(0f).setDuration(160).withEndAction(()->deleteTaskWithUndo(task)).start();
                    }else resetPosition();
                    swiping=false;return true;
                case MotionEvent.ACTION_CANCEL:
                    cancelDragHold();getParent().requestDisallowInterceptTouchEvent(false);dragging=false;clearDragPreview();resetPosition();swiping=false;return true;
            }
            return super.onTouchEvent(event);
        }
        void resetPosition(){setBackgroundColor(BACKGROUND);animate().translationX(0).translationY(0).alpha(1f).setDuration(160).start();}
        @Override protected void onDetachedFromWindow(){cancelDragHold();super.onDetachedFromWindow();}
    }

    @Override public void onCreate(Bundle b){ super.onCreate(b); prefs=getSharedPreferences("daily",MODE_PRIVATE); load(); shown=LocalDate.now(); build(); render(); }
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN&&activeEditor!=null){Rect bounds=new Rect();activeEditor.getGlobalVisibleRect(bounds);if(!bounds.contains((int)event.getRawX(),(int)event.getRawY()))finishInlineEdit();}
        return super.dispatchTouchEvent(event);
    }
    int dp(int n){ return (int)(n*getResources().getDisplayMetrics().density+.5f); }
    TextView text(String s,int sp,int color){ TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);return v; }
    GradientDrawable bg(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    String versionLabel(){
        try{PackageInfo info=getPackageManager().getPackageInfo(getPackageName(),0);long code=Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;return "Daily List "+info.versionName+" · build "+code;}catch(Exception ignored){return "Daily List";}
    }
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
        addButton=new Button(this);addButton.setText("＋  Add a task");addButton.setTextSize(17);addButton.setTextColor(Color.WHITE);addButton.setBackground(bg(BLUE,16));addButton.setOnClickListener(v->addDialog());root.addView(addButton,new LinearLayout.LayoutParams(-1,dp(58)));
        TextView versionFooter=text(versionLabel(),11,MUTED);versionFooter.setGravity(Gravity.CENTER);versionFooter.setContentDescription(versionFooter.getText().toString().replace(" · build ",", Android version code "));root.addView(versionFooter,new LinearLayout.LayoutParams(-1,dp(26)));setContentView(root);
    }
    void render(){
        LocalDate now=LocalDate.now();title.setText(shown.equals(now)?"Today":shown.equals(now.plusDays(1))?"Tomorrow":shown.format(DateTimeFormatter.ofPattern("EEEE")));
        subtitle.setText(shown.format(DateTimeFormatter.ofPattern("d MMMM yyyy")));list.removeAllViews();
        styleDayButton(todayButton,shown.equals(now));styleDayButton(tomorrowButton,shown.equals(now.plusDays(1)));
        boolean any=false;for(Task t:tasks)if(t.date.equals(shown.toString())){addTaskRow(t);any=true;}
        if(shown.equals(now)){
            ArrayList<Task> old=new ArrayList<>();for(Task t:tasks)if(!isSubtask(t)&&LocalDate.parse(t.date).isBefore(now)&&needsCarry(t)&&!carried(t.id,now))old.add(t);
            if(!old.isEmpty()){ TextView h=text("FROM PREVIOUS DAYS",12,MUTED);h.setTypeface(Typeface.DEFAULT,Typeface.BOLD);h.setPadding(dp(4),dp(26),0,dp(8));list.addView(h);for(Task t:old)addCarryGroup(t);any=true; }
        }
        if(!any){TextView empty=text("Nothing planned yet.\nEnjoy the space — or add a task below.",16,MUTED);empty.setGravity(Gravity.CENTER);empty.setPadding(0,dp(80),0,0);list.addView(empty,new LinearLayout.LayoutParams(-1,dp(180)));}
    }
    void styleDayButton(Button button,boolean selected){button.setTextColor(selected?Color.WHITE:BLUE);button.setBackground(bg(selected?BLUE:PALE_BLUE,14));}
    Task findTask(long id){for(Task t:tasks)if(t.id==id)return t;return null;}
    boolean isSubtask(Task t){Task parent=findTask(t.parent);return t.parent!=0&&parent!=null&&parent.date.equals(t.date);}
    ArrayList<Task> subtasks(Task parent,boolean incompleteOnly){ArrayList<Task> result=new ArrayList<>();for(Task t:tasks)if(t.parent==parent.id&&t.date.equals(parent.date)&&(!incompleteOnly||!t.done))result.add(t);return result;}
    boolean needsCarry(Task parent){return !parent.done||!subtasks(parent,true).isEmpty();}
    long newTaskId(){long id=System.currentTimeMillis();while(findTask(id)!=null)id++;return id;}
    boolean carried(long source,LocalDate day){for(Task t:tasks)if(t.source==source&&t.date.equals(day.toString()))return true;return false;}
    void addTaskRow(Task t){
        FrameLayout swipeLayer=new FrameLayout(this);swipeLayer.setTag(t);TextView deleteHint=text("Delete",14,Color.WHITE);deleteHint.setGravity(Gravity.CENTER);deleteHint.setTypeface(Typeface.DEFAULT,Typeface.BOLD);deleteHint.setBackgroundColor(DELETE);
        FrameLayout.LayoutParams deleteParams=new FrameLayout.LayoutParams(dp(92),-1,Gravity.END);swipeLayer.addView(deleteHint,deleteParams);
        SwipeTaskRow row=new SwipeTaskRow(t);
        CheckBox cb=new CheckBox(this);cb.setChecked(t.done);cb.setLongClickable(false);cb.setButtonTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{BLUE,MUTED}));
        EditText label=new EditText(this);label.setText(t.text);label.setTextSize(isSubtask(t)?16:17);label.setTextColor(t.done?MUTED:INK);label.setGravity(Gravity.CENTER_VERTICAL);label.setSingleLine(true);label.setImeOptions(EditorInfo.IME_ACTION_DONE);label.setPadding(dp(6),0,dp(4),0);label.setBackgroundColor(Color.TRANSPARENT);label.setFocusable(false);label.setCursorVisible(false);label.setLongClickable(false);if(t.done)label.setPaintFlags(label.getPaintFlags()|android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        cb.setOnCheckedChangeListener((b,x)->{t.done=x;save();render();});label.setOnClickListener(v->beginInlineEdit(label,t));label.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_DONE){finishInlineEdit();return true;}return false;});label.setOnFocusChangeListener((v,focused)->{if(!focused&&activeEditor==label)finishInlineEdit();});row.addView(cb,new LinearLayout.LayoutParams(dp(48),dp(52)));row.addView(label,new LinearLayout.LayoutParams(0,dp(52),1));swipeLayer.addView(row,new FrameLayout.LayoutParams(-1,-1));list.addView(swipeLayer,new LinearLayout.LayoutParams(-1,dp(62)));
        View line=new View(this);line.setTag(t);line.setBackgroundColor(DIVIDER);list.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
    }
    void addCarryGroup(Task old){
        LinearLayout group=new LinearLayout(this);group.setOrientation(LinearLayout.VERTICAL);group.setClickable(true);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),0,0,0);TextView plus=text("＋",23,BLUE);TextView label=text(old.text,16,Color.rgb(137,153,168));label.setPadding(dp(8),0,0,0);row.addView(plus,new LinearLayout.LayoutParams(dp(42),dp(52)));row.addView(label,new LinearLayout.LayoutParams(0,dp(52),1));group.addView(row);
        for(Task child:subtasks(old,true)){LinearLayout childRow=new LinearLayout(this);childRow.setGravity(Gravity.CENTER_VERTICAL);childRow.setPadding(dp(50),0,0,0);TextView branch=text("↳",17,MUTED);TextView childLabel=text(child.text,15,Color.rgb(137,153,168));childLabel.setPadding(dp(5),0,0,0);childRow.addView(branch,new LinearLayout.LayoutParams(dp(28),dp(42)));childRow.addView(childLabel,new LinearLayout.LayoutParams(0,dp(42),1));group.addView(childRow);}
        group.setOnClickListener(v->carryForward(old));list.addView(group);
    }
    void carryForward(Task old){
        LocalDate day=LocalDate.now();long parentId=newTaskId();Task copy=new Task(parentId,old.text,day.toString(),false,old.id,0);tasks.add(copy);for(Task child:subtasks(old,true))tasks.add(new Task(newTaskId(),child.text,day.toString(),false,child.id,parentId));save();render();
    }
    ArrayList<Task> tasksForDate(String date){ArrayList<Task> result=new ArrayList<>();for(Task t:tasks)if(t.date.equals(date))result.add(t);return result;}
    ArrayList<Task> dragBlock(Task task){ArrayList<Task> block=new ArrayList<>();block.add(task);if(!isSubtask(task))block.addAll(subtasks(task,false));return block;}
    View taskContainer(Task task){for(int i=0;i<list.getChildCount();i++){View child=list.getChildAt(i);if(child instanceof FrameLayout&&child.getTag()==task)return child;}return null;}
    void setTaskPreviewY(Task task,float y,boolean animate){
        for(int i=0;i<list.getChildCount();i++){View view=list.getChildAt(i);if(view.getTag()!=task)continue;if(!animate){view.animate().cancel();view.setTranslationY(y);continue;}Float target=dragPreviewTargets.get(view);if(target!=null&&Math.abs(target-y)<.5f)continue;dragPreviewTargets.put(view,y);view.animate().cancel();view.animate().translationY(y).setDuration(105).start();}
    }
    DragPlacement calculatePlacement(Task task,ArrayList<Task> remaining,int rawInsert,float dragX,boolean movingUp,int blockSize){
        int insert=Math.max(0,Math.min(rawInsert,remaining.size()));long parent=isSubtask(task)?task.parent:0;
        if(dragX>dp(40)&&blockSize==1){parent=0;for(int i=Math.min(insert-1,remaining.size()-1);i>=0;i--){Task candidate=remaining.get(i);if(!isSubtask(candidate)){parent=candidate.id;break;}}}else if(dragX<-dp(40))parent=0;
        if(blockSize>1)parent=0;
        if(parent!=0){int parentIndex=-1;for(int i=0;i<remaining.size();i++)if(remaining.get(i).id==parent){parentIndex=i;break;}if(parentIndex<0)parent=0;else{int groupEnd=parentIndex+1;while(groupEnd<remaining.size()&&remaining.get(groupEnd).parent==parent)groupEnd++;insert=Math.max(parentIndex+1,Math.min(insert,groupEnd));}}
        if(parent==0&&insert<remaining.size()&&isSubtask(remaining.get(insert))){long owner=remaining.get(insert).parent;int parentIndex=0;while(parentIndex<remaining.size()&&remaining.get(parentIndex).id!=owner)parentIndex++;int groupEnd=parentIndex+1;while(groupEnd<remaining.size()&&remaining.get(groupEnd).parent==owner)groupEnd++;insert=movingUp?parentIndex:groupEnd;}
        return new DragPlacement(Math.max(0,Math.min(insert,remaining.size())),parent);
    }
    DragPlacement previewTaskDrag(Task task,float dragX,float dragY){
        ArrayList<Task> order=tasksForDate(task.date);int from=order.indexOf(task);ArrayList<Task> block=dragBlock(task);if(from<0)return new DragPlacement(0,task.parent);ArrayList<Task> remaining=new ArrayList<>(order);remaining.removeAll(block);
        int rawInsert=from+Math.round(dragY/(float)dp(63));DragPlacement placement=calculatePlacement(task,remaining,rawInsert,dragX,dragY<0,block.size());int shift=block.size()*dp(63);
        for(Task moved:block){setTaskPreviewY(moved,dragY,false);View container=taskContainer(moved);if(container!=null){container.setAlpha(.92f);container.setElevation(dp(8));container.setTranslationX(moved==task?Math.max(-dp(52),Math.min(dp(52),dragX)):0);}}
        for(int i=0;i<remaining.size();i++){float target=0;if(placement.insert>from&&i>=from&&i<placement.insert)target=-shift;else if(placement.insert<from&&i>=placement.insert&&i<from)target=shift;setTaskPreviewY(remaining.get(i),target,true);}
        return placement;
    }
    void clearDragPreview(){
        for(int i=0;i<list.getChildCount();i++){View view=list.getChildAt(i);if(!(view.getTag() instanceof Task))continue;view.animate().cancel();view.setTranslationX(0);view.setTranslationY(0);view.setAlpha(1);view.setElevation(0);}dragPreviewTargets.clear();
    }
    void finishTaskDrag(Task task,DragPlacement placement,float dragX,float dragY){
        if(Math.abs(dragX)<dp(30)&&Math.abs(dragY)<dp(31))return;if(activeEditTask==task)finishInlineEdit();ArrayList<Task> order=tasksForDate(task.date);ArrayList<Task> block=dragBlock(task);if(!order.contains(task))return;order.removeAll(block);int insert=Math.max(0,Math.min(placement.insert,order.size()));task.parent=placement.parent;order.addAll(insert,block);applyDateOrder(task.date,order);save();render();
    }
    void applyDateOrder(String date,ArrayList<Task> order){ArrayList<Integer> slots=new ArrayList<>();for(int i=0;i<tasks.size();i++)if(tasks.get(i).date.equals(date))slots.add(i);for(int i=0;i<slots.size();i++)tasks.set(slots.get(i),order.get(i));}
    void addDialog(){
        LinearLayout box=new LinearLayout(this);box.setPadding(dp(24),dp(8),dp(24),0);box.setOrientation(LinearLayout.VERTICAL);EditText input=new EditText(this);input.setHint("What needs doing?");input.setSingleLine(true);box.addView(input);RadioGroup group=new RadioGroup(this);group.setOrientation(RadioGroup.HORIZONTAL);RadioButton cur=new RadioButton(this);cur.setText(shown.equals(LocalDate.now().plusDays(1))?"Tomorrow":shown.equals(LocalDate.now())?"Today":shown.format(DateTimeFormatter.ofPattern("EEE d MMM")));cur.setId(1);cur.setChecked(true);RadioButton next=new RadioButton(this);next.setText("Next day");next.setId(2);group.addView(cur);group.addView(next);box.addView(group);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("New task").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Add",null).create();d.setOnShowListener(x->{d.getButton(-1).setOnClickListener(v->{String s=input.getText().toString().trim();if(s.isEmpty()){input.setError("Enter a task");return;}LocalDate date=group.getCheckedRadioButtonId()==2?shown.plusDays(1):shown;tasks.add(new Task(newTaskId(),s,date.toString(),false,0,0));save();d.dismiss();render();});input.requestFocus();d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);});d.show();
    }
    void beginInlineEdit(EditText editor,Task task){
        if(activeEditor==editor)return;finishInlineEdit();activeEditor=editor;activeEditTask=task;editor.setFocusableInTouchMode(true);editor.setFocusable(true);editor.setCursorVisible(true);editor.setBackground(bg(PALE_BLUE,8));editor.requestFocus();editor.setSelection(editor.getText().length());handler.post(()->{if(activeEditor==editor)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(editor,InputMethodManager.SHOW_IMPLICIT);});
    }
    void finishInlineEdit(){
        if(activeEditor==null)return;EditText editor=activeEditor;Task task=activeEditTask;String updated=editor.getText().toString().trim();activeEditor=null;activeEditTask=null;if(updated.isEmpty())editor.setText(task.text);else if(!updated.equals(task.text)){task.text=updated;save();}editor.setCursorVisible(false);editor.setBackgroundColor(Color.TRANSPARENT);editor.setFocusable(false);editor.clearFocus();((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(editor.getWindowToken(),0);
    }
    void deleteTaskWithUndo(Task t){if(activeEditTask==t)finishInlineEdit();int index=tasks.indexOf(t);if(index<0)return;pendingPromotedChildren.clear();if(!isSubtask(t))for(Task child:subtasks(t,false)){child.parent=0;pendingPromotedChildren.add(child);}tasks.remove(index);save();render();showUndo(t,index);}
    void showUndo(Task task,int index){
        if(dismissUndo!=null)handler.removeCallbacks(dismissUndo);pendingDeletedTask=task;pendingDeletedIndex=index;
        if(undoBar==null){
            undoBar=new LinearLayout(this);undoBar.setGravity(Gravity.CENTER_VERTICAL);undoBar.setPadding(dp(18),0,dp(10),0);undoBar.setBackground(bg(INK,14));TextView message=text("Task deleted",15,Color.WHITE);TextView undo=text("UNDO",14,PALE_BLUE);undo.setTypeface(Typeface.DEFAULT,Typeface.BOLD);undo.setGravity(Gravity.CENTER);undo.setPadding(dp(18),0,dp(8),0);undo.setClickable(true);undo.setFocusable(true);undo.setOnClickListener(v->undoDelete());undoBar.addView(message,new LinearLayout.LayoutParams(0,-1,1));undoBar.addView(undo,new LinearLayout.LayoutParams(dp(84),-1));
        }
        if(undoBar.getParent()==null){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.setMargins(0,dp(8),0,dp(8));root.addView(undoBar,root.indexOfChild(addButton),p);undoBar.setAlpha(0f);undoBar.setTranslationY(dp(10));undoBar.animate().alpha(1f).translationY(0).setDuration(140).start();}
        dismissUndo=()->{pendingDeletedTask=null;pendingDeletedIndex=-1;pendingPromotedChildren.clear();hideUndo();};handler.postDelayed(dismissUndo,4500);
    }
    void undoDelete(){
        if(pendingDeletedTask==null)return;if(dismissUndo!=null)handler.removeCallbacks(dismissUndo);int index=Math.max(0,Math.min(pendingDeletedIndex,tasks.size()));Task restored=pendingDeletedTask;tasks.add(index,restored);for(Task child:pendingPromotedChildren)if(tasks.contains(child))child.parent=restored.id;pendingPromotedChildren.clear();pendingDeletedTask=null;pendingDeletedIndex=-1;save();render();hideUndo();
    }
    void hideUndo(){if(undoBar!=null&&undoBar.getParent()!=null){undoBar.animate().cancel();root.removeView(undoBar);}}
    @Override protected void onPause(){finishInlineEdit();super.onPause();}
    @Override protected void onDestroy(){if(dismissUndo!=null)handler.removeCallbacks(dismissUndo);super.onDestroy();}
    void load(){try{JSONArray a=new JSONArray(prefs.getString("tasks","[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);tasks.add(new Task(o.getLong("id"),o.getString("text"),o.getString("date"),o.optBoolean("done"),o.optLong("source"),o.optLong("parent")));}}catch(Exception ignored){}}
    void save(){JSONArray a=new JSONArray();try{for(Task t:tasks){JSONObject o=new JSONObject();o.put("id",t.id);o.put("text",t.text);o.put("date",t.date);o.put("done",t.done);o.put("source",t.source);o.put("parent",t.parent);a.put(o);}}catch(Exception ignored){}prefs.edit().putString("tasks",a.toString()).apply();}
}
