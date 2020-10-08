/*
 * MIT License
 *
 * Copyright (c) 2020 Artipie
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.artipie.http.client.auth;

import com.artipie.asto.Content;
import com.artipie.asto.ext.PublisherAs;
import com.artipie.http.Headers;
import com.artipie.http.client.ClientSlices;
import com.artipie.http.client.UriClientSlice;
import com.artipie.http.headers.Authorization;
import com.artipie.http.headers.WwwAuthenticate;
import com.artipie.http.rq.RequestLine;
import com.artipie.http.rq.RqMethod;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Bearer authenticator using specified authenticator and format to get required token.
 *
 * @since 0.4
 */
public final class BearerAuthenticator implements Authenticator {

    /**
     * Client slices.
     */
    private final ClientSlices client;

    /**
     * Token format.
     */
    private final TokenFormat format;

    /**
     * Ctor.
     *
     * @param client Client slices.
     * @param format Token format.
     */
    public BearerAuthenticator(final ClientSlices client, final TokenFormat format) {
        this.client = client;
        this.format = format;
    }

    @Override
    public CompletionStage<Headers> authenticate(final Headers headers) {
        return this.authenticate(new WwwAuthenticate(headers)).thenApply(Headers.From::new);
    }

    /**
     * Creates 'Authorization' header using requirements from 'WWW-Authenticate'.
     *
     * @param header WWW-Authenticate header.
     * @return Authorization header.
     */
    private CompletionStage<Authorization.Bearer> authenticate(final WwwAuthenticate header) {
        final URI realm;
        try {
            realm = new URI(header.realm());
        } catch (final URISyntaxException ex) {
            throw new IllegalArgumentException(ex);
        }
        final CompletableFuture<String> promise = new CompletableFuture<>();
        return new UriClientSlice(this.client, realm).response(
            new RequestLine(RqMethod.GET, "").toString(),
            Headers.EMPTY,
            Content.EMPTY
        ).send(
            (status, headers, body) -> new PublisherAs(body).bytes()
                .thenApply(this.format::token)
                .thenCompose(
                    token -> {
                        promise.complete(token);
                        return CompletableFuture.allOf();
                    }
                )
        ).thenCompose(ignored -> promise).thenApply(Authorization.Bearer::new);
    }
}
