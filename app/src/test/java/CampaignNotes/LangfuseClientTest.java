package CampaignNotes;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class LangfuseClientTest {
    private LangfuseClient client;
    @Before
    public void setUp() {
        client = new LangfuseClient();
    }

    @Test
    public void connectionToLangfuseTest(){
        Assert.assertTrue(client.checkConnection());
    }
}
