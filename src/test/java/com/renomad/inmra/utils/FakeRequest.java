package com.renomad.inmra.utils;

import com.renomad.minum.web.*;

import java.util.List;

public class FakeRequest implements IRequest {
    public RequestLine requestLine;
    public String headerString;

    @Override
    public Headers getHeaders() {
        return new Headers(List.of(headerString));
    }

    @Override
    public RequestLine getRequestLine() {
        return requestLine;
    }

    @Override
    public Body getBody() {
        return null;
    }

    @Override
    public String getRemoteRequester() {
        return null;
    }

    @Override
    public ISocketWrapper getSocketWrapper() {
        return null;
    }

    @Override
    public Iterable<UrlEncodedKeyValue> getUrlEncodedIterable() {
        return null;
    }

    @Override
    public Iterable<StreamingMultipartPartition> getMultipartIterable() {
        return null;
    }

    @Override
    public boolean hasAccessedBody() {
        return false;
    }
}
