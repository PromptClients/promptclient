package com.promptclient.util;

/** Exposes the last sprint state vanilla uses to decide whether a sprint packet is needed. */
public interface ClientPlayerEntityAccess {
	boolean promptclient$getLastSprinting();

	void promptclient$setLastSprinting(boolean sprinting);

	double promptclient$getLastX();

	double promptclient$getLastBaseY();

	double promptclient$getLastZ();
}
