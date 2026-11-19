/*
 * Copyright © 2026 James Carman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jwcarman.migratory.autoconfigure;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * Serves the root changelog migratory generates, which includes each declared changelog in order,
 * and delegates every other location, so Liquibase resolves the includes as an application's own
 * master changelog would.
 */
final class RootChangelogResourceLoader implements ResourcePatternResolver {

  static final String ROOT_PATH = "META-INF/migratory/root-changelog.xml";
  static final String ROOT_LOCATION = "classpath:" + ROOT_PATH;

  private final ResourcePatternResolver delegate;
  private final Resource root;

  RootChangelogResourceLoader(ResourceLoader delegate, String rootChangelog) {
    this.delegate = new PathMatchingResourcePatternResolver(delegate);
    this.root = new RootResource(rootChangelog.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public Resource getResource(String location) {
    return isRoot(location) ? root : delegate.getResource(location);
  }

  @Override
  public Resource[] getResources(String locationPattern) throws IOException {
    return isRoot(locationPattern) ? new Resource[] {root} : delegate.getResources(locationPattern);
  }

  @Override
  public ClassLoader getClassLoader() {
    return delegate.getClassLoader();
  }

  private static boolean isRoot(String location) {
    String path = location;
    for (String prefix : new String[] {CLASSPATH_ALL_URL_PREFIX, CLASSPATH_URL_PREFIX}) {
      if (path.startsWith(prefix)) {
        path = path.substring(prefix.length());
        break;
      }
    }
    while (path.startsWith("/")) {
      path = path.substring(1);
    }
    return ROOT_PATH.equals(path);
  }

  /** The generated root changelog, held in memory. */
  private static final class RootResource extends AbstractResource {

    private final byte[] content;

    private RootResource(byte[] content) {
      this.content = content;
    }

    @Override
    public boolean exists() {
      return true;
    }

    @Override
    public URI getURI() {
      return URI.create(ROOT_LOCATION);
    }

    @Override
    public String getFilename() {
      return "root-changelog.xml";
    }

    @Override
    public long contentLength() {
      return content.length;
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(content);
    }

    @Override
    public String getDescription() {
      return "migratory's generated root changelog";
    }

    @Override
    public boolean equals(Object other) {
      return other == this;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }
  }
}
