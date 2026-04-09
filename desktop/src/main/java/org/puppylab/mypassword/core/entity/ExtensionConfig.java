package org.puppylab.mypassword.core.entity;

import jakarta.persistence.Id;

/**
 * Extension pair.
 */
public class ExtensionConfig {

    @Id
    public long id;

    public boolean approve;

    // random seed "a1b2c3"
    public String seed;

    // MyPassword Chrome Extension v1.0
    public String name;

    // Windows 11 (Homer's PC)
    public String device;

}
