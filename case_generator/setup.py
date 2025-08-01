from setuptools import setup, find_packages

with open("README.md", "r") as readme_file:
    readme = readme_file.read()

requirements = []

setup(
    name="case_generator",
    version="0.0.1",
    author="Madi Ramsey",
    author_email="mramsey@mitre.org",
    description="A framework to generate synthetic cases for the bayesian grace risk-based scheduling project.",
    long_description=readme,
    long_description_content_type="text/markdown",
   url="git@github.com:mitre/risk-based-scheduling.git",
    packages=find_packages(),
    install_requires=requirements,
    classifiers=[
        "Programming Language :: Python :: 3.9",
        "License :: ",
    ],
)
