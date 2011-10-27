package org.hogeika.android.app.Contacts.plugin.gmail;

import java.io.IOException;

public interface SaslClient {
    public String getMechanismName();
//    public boolean hasInitialResponse();
    public byte[] evaluateChallenge(byte[] challenge) throws IOException /* SaslException */;
    public boolean isComplete();
//    public byte[] unwrap(byte[] incoming, int offset, int len);
//    public byte[] wrap(byte[] outgoing, int offset, int len);
    public Object getNegotiatedProperty(String propName);
//    public void dispose() throws SaslException;
}
