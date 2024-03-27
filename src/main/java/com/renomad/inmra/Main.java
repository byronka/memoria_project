package com.renomad.inmra;

import com.renomad.inmra.migrations.DatabaseMigration;
import com.renomad.inmra.utils.MemoriaContext;
import com.renomad.minum.Constants;
import com.renomad.minum.Context;
import com.renomad.minum.web.FullSystem;

import static com.renomad.inmra.utils.MemoriaContext.buildMemoriaContext;

public class Main {

    public static void main(String[] args) throws Exception {

        Context context = FullSystem.buildContext();
        var fullSystem = new FullSystem(context);

        MemoriaContext memoriaContext = buildMemoriaContext(context);

        // migrate the database.  This is run every time the system starts,
        // as a guarantee that the data has a proper expected format.
        // it's a bit nuanced, but even though it runs every time, it shouldn't
        // end up *changing* anything unless it sees there is a version change.
        new DatabaseMigration(context, memoriaContext).migrate();

        // kick off the web server and instantiate everything in Minum
        fullSystem.start();

        // Register some endpoints
        new TheRegister(fullSystem.getContext(), memoriaContext).registerDomains();

        // show an indicator that the system is ready to use
        Constants constants = context.getConstants();
        System.out.printf(
                "\n\nSystem is ready.  Access at http://%s:%d or https://%s:%d\n\n",
                constants.hostName,
                constants.serverPort,
                constants.hostName,
                constants.secureServerPort);

        fullSystem.block();
    }

}
