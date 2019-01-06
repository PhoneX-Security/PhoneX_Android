package net.phonex.autologinQuick;

import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.MediumTest;

import net.phonex.autologin.LoginCredentials;
import net.phonex.autologin.exceptions.PasswordPersisterException;
import net.phonex.autologin.exceptions.ServiceUnavailableException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Random;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class QuickLoginPersisterTest {
    private QuickLoginPersister quickLoginPersister;

    @Before
    public void createPersister(){
        quickLoginPersister = new QuickLoginPersister(InstrumentationRegistry.getContext());
    }

    @Test
    public void persister_storeAndLoadCredentials() throws PasswordPersisterException, ServiceUnavailableException {
        Random generator = new Random();
        // always store different password
        String password = "secret" + generator.nextInt();

        LoginCredentials credentials = new LoginCredentials(password, "test", "phone-x.net");
        quickLoginPersister.storeCredentials(credentials);
        LoginCredentials loadedCredentials = quickLoginPersister.loadCredentials();

        assertThat(credentials, is(loadedCredentials));
    }
}
