package org.cocos2d.actions;

import fj.Equal;
import fj.F;
import fj.Ord;
import fj.Unit;
import fj.data.HashMap;
import fj.data.List;
import fj.data.Option;
import org.cocos2d.config.ccConfig;

import java.lang.reflect.Method;

/** Scheduler is responsible of triggering the scheduled callbacks.
 You should not use NSTimer. Instead use this class.
 
 There are 2 different types of callbacks (selectors):

	- update selector: the 'update' selector will be called every frame. You can customize the priority.
	- custom selector: A custom selector will be called every frame, or with a custom interval of time
 
 The 'custom selectors' should be avoided when possible. It is faster, and consumes less memory to use the 'update selector'.

*/
public class CCScheduler {

    private static class UpdateCallback {
        // struct	_listEntry *prev, *next;
        public Method impMethod;
        public org.cocos2d.actions.UpdateCallback callback; // instead of method invocation
        public Object	target;				// not retained (retained by hashUpdateEntry)
        public int		priority;
        public boolean	paused;
    };

    private static class TimerSchedules {
        UpdateCallback entry;
        List<CCTimer> timers = List.nil();
        Object target;		// hash key (retained)
        boolean	paused;

        void setPaused(boolean b){
            paused = b;
            if (entry != null){
                entry.paused = b;
            }
        }
    }

    // most of the updates are going to be 0, that's why there
    // is an special list for updates with priority 0
    private List<UpdateCallback> updatesNeg = List.nil();	// list of priority < 0
    private List<UpdateCallback> updates0 = List.nil();	// list priority == 0
    private List<UpdateCallback> updatesPos = List.nil();	// list priority > 0

	// Used for "selectors with interval"
	HashMap<Object, TimerSchedules> hashForSelectors = HashMap.hashMap();
	HashMap<Object, TimerSchedules>  hashForUpdates = HashMap.hashMap();
    
	// Optimization
//	Method			    impMethod;
	private final String updateSelector = "update";

    /** Modifies the time of all scheduled callbacks.
      You can use this property to create a 'slow motion' or 'fast fordward' effect.
      Default is 1.0. To create a 'slow motion' effect, use values below 1.0.
      To create a 'fast fordward' effect, use values higher than 1.0.
      @since v0.8
      @warning It will affect EVERY scheduled selector / action.
    */
    private float timeScale_ = 1.0f;

    private static CCScheduler _sharedScheduler = null;

    private CCScheduler() {}

    /** returns a shared instance of the Scheduler */
    public static CCScheduler sharedScheduler() {
        if (_sharedScheduler != null) {
            return _sharedScheduler;
        }
        synchronized (CCScheduler.class) {
            if (_sharedScheduler == null) {
                _sharedScheduler = new CCScheduler();
            }
            return _sharedScheduler;
        }
    }

    /** purges the shared scheduler. It releases the retained instance.
      @since v0.99.0
      */
    public static void purgeSharedScheduler() {
        _sharedScheduler = null;
    }

    public float getTimeScale() {
        return timeScale_;
    }

    public void setTimeScale(float ts) {
        timeScale_ = ts;
    }

    private void updateSubscribers(List<UpdateCallback> subscribers, float dt) {
        for (UpdateCallback e : subscribers) {
            if( ! e.paused ) {
                if(e.callback !=null) {
                    e.callback.update(dt);
                } else {
                    try {
                        e.impMethod.invoke(e.target, dt);
                    } catch (Exception e1) {
                        e1.printStackTrace();
                    }
                }
            }
        }
    }

    private List<UpdateCallback> updates(int priority) {
        if (priority > 0) {
            return updatesPos;
        } else if (priority < 0) {
            return updatesNeg;
        } else {
            return updates0;
        }
    }

    /** 'tick' the scheduler.
      You should NEVER call this method, unless you know what you are doing.
    */
    public void tick(float dt) {
        dt *= timeScale_;
        
        updateSubscribers(updatesNeg, dt);
        updateSubscribers(updates0, dt);
        updateSubscribers(updatesPos, dt);

        for (TimerSchedules elt : hashForSelectors.values()) {
            if (!elt.paused) {
                final List<CCTimer> timersCopy = elt.timers;
                for (CCTimer timer : timersCopy) {
                    timer.update(dt);
                }
            }
        }
    }

    /** The scheduled method will be called every 'interval' seconds.
      If paused is YES, then it won't be called until it is resumed.
      If 'interval' is 0, it will be called every frame, but if so, it recommened to use 'scheduleUpdateForTarget:' instead.

      @since v0.99.3
    */
    public void schedule(String selector, Object target, float interval, boolean paused) {
        assert selector != null: "Argument selector must be non-nil";
        addTimer(target, new CCTimer(target, selector, interval), paused);
    }
    
    /*
     * This is java way version, uses interface based callbacks. UpdateCallback in this case.
     * It would be preffered solution. It is more polite to Java, GC, and obfuscation.  
     */
    public void schedule(org.cocos2d.actions.UpdateCallback callback, Object target, float interval, boolean paused) {
        assert callback != null: "Argument callback must be non-nil";
        addTimer(target, new CCTimer(target, callback, interval), paused);
    }

    private void addTimer(Object target, CCTimer timer, boolean paused) {
        assert target != null: "Argument target must be non-nil";

        TimerSchedules element = hashForSelectors.get(target).toNull();
        if (element == null) {
            element = new TimerSchedules();
            element.target = target;
            hashForSelectors.set(target, element);
            // Is this the 1st element ? Then set the pause level to all the selectors of this target
            element.paused = paused;
        } else {
            assert element.paused == paused : "CCScheduler. Trying to schedule a selector with a pause value different than the target";
        }

        element.timers = element.timers.snoc(timer);
    }

    /** Unshedules a selector for a given target.
     If you want to unschedule the "update", use unscheudleUpdateForTarget.
     @since v0.99.3
    */
    public void unschedule(final String selector, Object target) {
        if (selector == null) {
            return;
        }

        unschedule(target, new F<CCTimer, Boolean>() {
            @Override
            public Boolean f(CCTimer timer) {
                return selector.equals(timer.getSelector());
            }
        });
    }
    
    /*
     * This is java way version, uses interface based callbacks. UpdateCallback in this case.
     * It would be preffered solution. It is more polite to Java, GC, and obfuscation.  
     */
    public void unschedule(final org.cocos2d.actions.UpdateCallback callback, Object target) {
        if (callback == null) {
            return;
        }

        unschedule(target, new F<CCTimer, Boolean>() {
            @Override
            public Boolean f(CCTimer timer) {
                return timer.getCallback() == callback;
            }
        });
    }

    private void unschedule(Object target, F<CCTimer, Boolean> timerPredicate) {
        if (target == null) {
            return;
        }

        final TimerSchedules element = hashForSelectors.get(target).toNull();
        if (element != null) {
            final List<CCTimer> timersCopy = element.timers;

            for (CCTimer timer : timersCopy) {
                if (timerPredicate.f(timer)) {
                    element.timers = element.timers.delete(timer, Equal.<CCTimer>anyEqual());

                    if (element.timers.isEmpty()) {
                        hashForSelectors.delete(element.target);
                    }
                    return;
                }
            }
        }
    }

    /** Unschedules the update selector for a given target
      @since v0.99.3
      */
    public void unscheduleUpdate(Object target) {
        if (target == null) {
            return;
        }

        final Option<TimerSchedules> entry = hashForUpdates.get(target);
        if (entry.isSome()) {
            updates(entry.some().entry.priority).delete(entry.some().entry, Equal.<UpdateCallback>anyEqual());
        }
        hashForUpdates.delete(target);
    }

    /** Unschedules all selectors for a given target.
     This also includes the "update" selector.
     @since v0.99.3
    */
	public void unscheduleAllSelectors(Object target) {
        if( target == null )
            return;

        TimerSchedules element = hashForSelectors.get(target).toNull();
        if( element != null) {
            element.timers = List.nil();
            hashForSelectors.delete(element.target);
        }

        this.unscheduleUpdate(target);
	}

    private final F<TimerSchedules, Unit> unscheduleAllSelectors = new F<TimerSchedules, Unit>() {
        @Override
        public Unit f(TimerSchedules element) {
            unscheduleAllSelectors(element.target);
            return null;
        }
    };

    private final F<UpdateCallback,Unit> unscheduleUpdate = new F<UpdateCallback, Unit>() {
        @Override
        public Unit f(UpdateCallback entry) {
            unscheduleUpdate(entry.target);
            return null;
        }
    };

    /** Unschedules all selectors from all targets.
      You should NEVER call this method, unless you know what you are doing.

      @since v0.99.3
      */
    public void unscheduleAllSelectors() {
        hashForSelectors.values().foreach(unscheduleAllSelectors);
        updatesNeg.foreach(unscheduleUpdate);
        updates0.foreach(unscheduleUpdate);
        updatesPos.foreach(unscheduleUpdate);
    }

    /** Resumes the target.
     The 'target' will be unpaused, so all schedule selectors/update will be 'ticked' again.
     If the target is not present, nothing happens.
     @since v0.99.3
    */
	public void resume(Object target) {
        setPaused(target, false);
	}

    /** Pauses the target.
     All scheduled selectors/update for a given target won't be 'ticked' until the target is resumed.
     If the target is not present, nothing happens.
     @since v0.99.3
    */
	public void pause(Object target) {
        setPaused(target, true);
    }

    private void setPaused(Object target, boolean isPaused) {
        assert target != null: "target must be non nil";

        Option<TimerSchedules> element = hashForSelectors.get(target);
        if (element.isSome()) {
            element.some().paused = isPaused;
        }

        Option<TimerSchedules> elementUpdate = hashForUpdates.get(target);
        if (elementUpdate.isSome()) {
            elementUpdate.some().setPaused(isPaused);
        }
    }

    /**
     *  Schedules the 'update' selector for a given target with a given priority. The 'update' selector will be called every frame.
     * @param target an UpdateCallback instance or a POJO with <code>public void update(float dt)</code> method
     * @param priority etermines the invocation order. Positive, zero and negative are called separately in the specified order.
     * @param paused initial state of the pause flag
     *    @since v0.99.3
     */
	public void scheduleUpdate(Object target, int priority, boolean paused) {
        if (ccConfig.COCOS2D_DEBUG >= 1) {
            assert hashForUpdates.get(target).isNone() :"CCScheduler: You can't re-schedule an 'update' selector'. Unschedule it first";
        }

        if (priority == 0) {
        	updates0 = append(updates0, target, paused);
        } else if (priority < 0) {
        	updatesNeg = priority(updatesNeg, target, priority, paused);
        } else { // priority > 0
        	updatesPos = priority(updatesPos, target, priority, paused);
        }
	}
	
    @Override
    public void finalize () throws Throwable  {
        unscheduleAllSelectors();
        _sharedScheduler = null;

        super.finalize();
    }

    public List<UpdateCallback> append(List<UpdateCallback> list, Object target, boolean paused) {
        UpdateCallback listElement = new UpdateCallback();

        listElement.target = target;
        listElement.paused = paused;
        if(target instanceof org.cocos2d.actions.UpdateCallback) {
        	listElement.callback = (org.cocos2d.actions.UpdateCallback)target;
        } else {
            try {
    			listElement.impMethod = target.getClass().getMethod(updateSelector, Float.TYPE);
            } catch (NoSuchMethodException e) {
        		e.printStackTrace();
        	}       	
        }

        final List<UpdateCallback> newList = list.snoc(listElement);

        // update hash entry for quicker access
        TimerSchedules hashElement = new TimerSchedules();
        hashElement.target = target;
        hashElement.entry = listElement;
        hashForUpdates.set(target, hashElement);
        return newList;
    }

    private final F<UpdateCallback, Integer> getPriority = new F<UpdateCallback, Integer>() {
        @Override
        public Integer f(UpdateCallback entry) {
            return entry.priority;
        }
    };

    private final Ord<UpdateCallback> priorityOrd = Ord.intOrd.comap(getPriority);

    public List<UpdateCallback> priority(List<UpdateCallback> list, Object target, int priority, boolean paused) {
        UpdateCallback listElement = new UpdateCallback();

        listElement.target = target;
        listElement.priority = priority;
        listElement.paused = paused;
        if(target instanceof org.cocos2d.actions.UpdateCallback) {
        	listElement.callback = (org.cocos2d.actions.UpdateCallback)target;
        } else {
	        try {
				listElement.impMethod = target.getClass().getMethod(updateSelector, Float.TYPE);
	        } catch (NoSuchMethodException e) {
        		e.printStackTrace();
        	}
        }

        final List<UpdateCallback> newList = list.snoc(listElement).sort(priorityOrd);

        TimerSchedules hashElement = new TimerSchedules();
        hashElement.target = target;
        hashElement.entry = listElement;
        hashForUpdates.set(target, hashElement);
        return newList;
    }
}
