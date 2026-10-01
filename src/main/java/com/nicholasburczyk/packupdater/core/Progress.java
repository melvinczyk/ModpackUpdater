package com.nicholasburczyk.packupdater.core;

public interface Progress {

    Progress NO_OP = new Progress() {
        @Override
        public void message(String text) {
        }

        @Override
        public void step(int done, int total) {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    };

    void message(String text);

    void step(int done, int total);

    boolean isCancelled();
}
