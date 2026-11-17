-- ViralKit Supabase PostgreSQL Schema & Row-Level Security (RLS)
-- Run this SQL in your Supabase Project Dashboard -> SQL Editor.

create table if not exists public.creator_profiles (
    user_id text primary key,
    display_name text not null,
    email text,
    preferred_language text not null default 'English',
    preferred_platform text not null default 'Instagram Reels',
    fcm_topic text,
    updated_at timestamptz not null default now(),
    created_at timestamptz not null default now()
);

create table if not exists public.viral_growth_kits (
    id text primary key,
    user_id text not null,
    title text not null,
    media_label text not null default '',
    media_kind text not null default 'text' check (media_kind in ('video', 'audio', 'text')),
    language text not null default 'English',
    platform text not null default 'Instagram Reels',
    model_used text not null default 'gemini-4-hydrogen',
    viral_score integer not null default 0 check (viral_score >= 0 and viral_score <= 100),
    diagnosis_critique text not null default '',
    first_30s_strategy text not null default '',
    curiosity_hook text not null default '',
    contrarian_hook text not null default '',
    direct_value_hook text not null default '',
    pacing_segments jsonb not null default '[]'::jsonb,
    instagram_caption text not null default '',
    hashtags jsonb not null default '[]'::jsonb,
    youtube_title text not null default '',
    youtube_description text not null default '',
    thumbnail_ideas jsonb not null default '[]'::jsonb,
    trending_topics jsonb not null default '[]'::jsonb,
    trending_tag_sources jsonb not null default '[]'::jsonb,
    raw_json text not null default '{}',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_viral_growth_kits_user_created
    on public.viral_growth_kits (user_id, created_at desc);

-- Enable Row-Level Security
alter table public.creator_profiles enable row level security;
alter table public.viral_growth_kits enable row level security;

-- NOTE:
-- If using Supabase Auth JWTs (auth.uid()), replace `true` below with:
--   using (auth.uid()::text = user_id) with check (auth.uid()::text = user_id)
-- If mirroring from Firebase Auth UIDs over a custom JWT / Edge Function,
-- map the Firebase `sub` claim via `(auth.jwt() ->> 'sub') = user_id`.
create policy "creator_profiles_owner_policy"
    on public.creator_profiles
    for all
    using ((auth.jwt() ->> 'sub') = user_id)
    with check ((auth.jwt() ->> 'sub') = user_id);

create policy "viral_growth_kits_owner_policy"
    on public.viral_growth_kits
    for all
    using ((auth.jwt() ->> 'sub') = user_id)
    with check ((auth.jwt() ->> 'sub') = user_id);
