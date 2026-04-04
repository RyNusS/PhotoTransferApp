package com.family.phototransfer.network;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class ReceiverStateHolder_Factory implements Factory<ReceiverStateHolder> {
  @Override
  public ReceiverStateHolder get() {
    return newInstance();
  }

  public static ReceiverStateHolder_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static ReceiverStateHolder newInstance() {
    return new ReceiverStateHolder();
  }

  private static final class InstanceHolder {
    private static final ReceiverStateHolder_Factory INSTANCE = new ReceiverStateHolder_Factory();
  }
}
