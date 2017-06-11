package de.mio.futurelabbragi;

import net.gotev.speech.Speech;

public class Application extends android.app.Application {

    @Override
    public void onCreate() {
        super.onCreate();

        Speech.init(this,getPackageName());
        Speech.getInstance().setStopListeningAfterInactivity(999999);
    }

}
